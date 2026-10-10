#!/usr/bin/env bash
# prune.sh — GFS retention over the backup folder's file list (F018 spec "Sidecar/Naming and
# selection", ADR 0015). Pure function, no filesystem access: a newline-separated file list on
# stdin + "now" (arg 1, UTC ISO 8601) in, the subset to delete (one per line) on stdout. Exits 0
# even when nothing is deleted; exits non-zero only on a usage error (missing "now").
#
# Only names matching myfinances-<UTCyyyymmddThhmmssZ>[.pre-upgrade].dump.age are ever candidates
# for deletion - anything else on stdin (a foreign file, status.json, a pre-restore-<ts> safety
# dump, which uses a different naming scheme entirely) is silently left out of the output.
#
# GFS buckets (day = UTC calendar date, ISO week = date +%G-W%V so a week never mis-sorts across
# a calendar-year boundary, month = yyyy-mm) are built only from files that actually exist, then
# ranked by recency among themselves - the most recent 7 day buckets, 5 week buckets and 12 month
# buckets are each kept (the single newest file in that bucket). This is deliberately not a fixed
# calendar window anchored to "now": an on-demand stack with gaps still gets real historical depth
# (ADR 0015 - "deeper history than last N files") instead of mostly-empty day slots relative to
# today. "now" (arg 1) is accepted per the plan's "files + now" signature and is used only as a
# clock-skew guard: a file timestamped after "now" is never deleted and never counted toward any
# bucket's "newest" - see the ADR/plan judgment call noted in this feature's PR about this
# interpretation versus a fixed calendar window.
#
# pre-upgrade files are exempt from every rule above until a later (greater timestamp), ordinary
# (non-pre-upgrade) file exists among the candidates - then they are pruned under the same rules
# as any other file.

set -euo pipefail

NOW_ARG="${1:-}"
if [ -z "$NOW_ARG" ]; then
  echo "usage: prune.sh <now-iso8601> < file-list" >&2
  exit 2
fi
NOW_EPOCH=$(date -u -d "$NOW_ARG" +%s)

PATTERN='^myfinances-([0-9]{4})([0-9]{2})([0-9]{2})T([0-9]{2})([0-9]{2})([0-9]{2})Z(\.pre-upgrade)?\.dump\.age$'

names=()
epochs=()
day_keys=()
week_keys=()
month_keys=()
is_preupgrade=()

while IFS= read -r line || [ -n "$line" ]; do
  [ -z "$line" ] && continue
  if [[ "$line" =~ $PATTERN ]]; then
    y=${BASH_REMATCH[1]} mo=${BASH_REMATCH[2]} d=${BASH_REMATCH[3]}
    h=${BASH_REMATCH[4]} mi=${BASH_REMATCH[5]} s=${BASH_REMATCH[6]}
    iso="${y}-${mo}-${d} ${h}:${mi}:${s} UTC"
    epoch=$(date -u -d "$iso" +%s) || continue
    # Clock-skew guard: never consider, delete or let a future-dated file influence a bucket.
    [ "$epoch" -gt "$NOW_EPOCH" ] && continue
    names+=("$line")
    epochs+=("$epoch")
    day_keys+=("$(date -u -d "$iso" +%Y-%m-%d)")
    week_keys+=("$(date -u -d "$iso" +%G-W%V)")
    month_keys+=("$(date -u -d "$iso" +%Y-%m)")
    if [ -n "${BASH_REMATCH[7]:-}" ]; then
      is_preupgrade+=("1")
    else
      is_preupgrade+=("0")
    fi
  fi
done

count=${#names[@]}
if [ "$count" -eq 0 ]; then
  exit 0
fi

declare -A keep_index=()

# Marks index $1 as kept.
keep() { keep_index["$1"]=1; }

# Applies one bucket axis: given the per-file bucket-key array (nameref $1) and how many of the
# most-recent distinct bucket keys to keep ($2), finds each kept bucket's newest file and marks it.
apply_bucket_rule() {
  local -n keys_ref=$1
  local window=$2

  # Distinct keys, sorted ascending (lexicographic sort is chronological for all three key
  # formats used here: yyyy-mm-dd, %G-W%V and yyyy-mm).
  local -A seen=()
  local distinct=()
  local i
  for ((i = 0; i < count; i++)); do
    local k="${keys_ref[$i]}"
    if [ -z "${seen[$k]:-}" ]; then
      seen[$k]=1
      distinct+=("$k")
    fi
  done
  local sorted
  sorted=$(printf '%s\n' "${distinct[@]}" | sort)
  local -a sorted_keys=()
  while IFS= read -r k; do sorted_keys+=("$k"); done <<<"$sorted"

  local total=${#sorted_keys[@]}
  local start=$((total - window))
  [ "$start" -lt 0 ] && start=0

  local -a recent_keys=("${sorted_keys[@]:$start}")
  local -A recent_set=()
  for k in "${recent_keys[@]}"; do recent_set[$k]=1; done

  for k in "${recent_keys[@]}"; do
    local best_i=-1
    local best_epoch=-1
    for ((i = 0; i < count; i++)); do
      if [ "${keys_ref[$i]}" = "$k" ] && [ "${epochs[$i]}" -gt "$best_epoch" ]; then
        best_epoch=${epochs[$i]}
        best_i=$i
      fi
    done
    [ "$best_i" -ge 0 ] && keep "$best_i"
  done
}

apply_bucket_rule day_keys 7
apply_bucket_rule week_keys 5
apply_bucket_rule month_keys 12

# pre-upgrade exemption: kept unconditionally unless a later, ordinary file exists.
for ((i = 0; i < count; i++)); do
  [ "${is_preupgrade[$i]}" = "1" ] || continue
  released=0
  for ((j = 0; j < count; j++)); do
    if [ "${is_preupgrade[$j]}" = "0" ] && [ "${epochs[$j]}" -gt "${epochs[$i]}" ]; then
      released=1
      break
    fi
  done
  [ "$released" -eq 0 ] && keep "$i"
done

for ((i = 0; i < count; i++)); do
  [ -z "${keep_index[$i]:-}" ] && echo "${names[$i]}"
done
exit 0
