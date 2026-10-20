#!/usr/bin/env bats
# Table-driven tests for backup/scripts/prune.sh (F018 spec "Sidecar/Naming and selection",
# plan.md Phase 1). prune.sh is a pure function: a newline-separated file list on stdin + "now"
# (arg 1, UTC ISO 8601) in, the subset to delete (one per line) on stdout - no filesystem access,
# so every case here is just strings in, strings out.
#
# GFS buckets are built from whichever files exist (day = UTC calendar date, ISO week = date
# +%G-W%V, month = yyyy-mm), ranked by recency among themselves, then the most recent 7 day
# buckets / 5 week buckets / 12 month buckets are each kept (newest file in the bucket). This is
# deliberately *not* a fixed calendar window anchored to "now" (e.g. "the last 7 calendar days"):
# an on-demand stack backed up once a week would then mostly see empty day-slots relative to
# today and lose real depth. Walking back through however many buckets actually have files gives
# "deeper history than last N files" (ADR 0015) regardless of gaps. "now" is still accepted per
# the plan's "files + now" signature and is used as a clock-skew guard (never delete/count a file
# timestamped after "now"), but otherwise does not change which buckets are "the last N".

setup() {
  SCRIPT="$BATS_TEST_DIRNAME/../scripts/prune.sh"
  NOW="2026-03-15T12:00:00Z"
}

# Runs prune.sh with the given "now" and file names (one per remaining arg) on stdin.
run_prune() {
  local now=$1
  shift
  local input
  input=$(printf '%s\n' "$@")
  run bash "$SCRIPT" "$now" <<<"$input"
}

@test "daily bucket keeps only the newest file for a day with several backups (burst of restarts)" {
  run_prune "$NOW" \
    "myfinances-20260310T080000Z.dump.age" \
    "myfinances-20260310T200000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "myfinances-20260310T080000Z.dump.age" ]
}

@test "distinct days within a small window are all kept" {
  run_prune "$NOW" \
    "myfinances-20260308T100000Z.dump.age" \
    "myfinances-20260309T100000Z.dump.age" \
    "myfinances-20260310T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "more than 7 distinct day-buckets: only the 7 most recent survive the daily rule" {
  # 10 distinct days (Mar 2-11), all in the same ISO week pair (W10/W11) and the same month, so
  # the weekly rule only ever protects the newest of Mar 2-8 (Mar 8) and the monthly rule only
  # ever protects the single newest of the month (Mar 11) - days 2-4 have no other bucket backing
  # them up once the daily window's most-recent-7 cutoff (days 5-11) leaves them out.
  run_prune "2026-03-15T00:00:00Z" \
    "myfinances-20260302T100000Z.dump.age" \
    "myfinances-20260303T100000Z.dump.age" \
    "myfinances-20260304T100000Z.dump.age" \
    "myfinances-20260305T100000Z.dump.age" \
    "myfinances-20260306T100000Z.dump.age" \
    "myfinances-20260307T100000Z.dump.age" \
    "myfinances-20260308T100000Z.dump.age" \
    "myfinances-20260309T100000Z.dump.age" \
    "myfinances-20260310T100000Z.dump.age" \
    "myfinances-20260311T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "$(printf '%s\n' \
    "myfinances-20260302T100000Z.dump.age" \
    "myfinances-20260303T100000Z.dump.age" \
    "myfinances-20260304T100000Z.dump.age")" ]
}

@test "weekly bucket protects a file the daily rule alone would already have dropped" {
  # Jan 5 (Mon) and Jan 8 (Thu) 2026 are both ISO week 2026-W02. Seven filler days (Jan 12-18,
  # ISO week 2026-W03) push both out of the daily top-7 window, but W02 and W03 are only 2 of the
  # (well under 5) week buckets that exist, so each week's own newest file is still weekly-kept:
  # Jan 8 (the newer of the W02 pair) survives via the weekly rule alone; Jan 5 does not.
  run_prune "2026-02-01T00:00:00Z" \
    "myfinances-20260105T100000Z.dump.age" \
    "myfinances-20260108T100000Z.dump.age" \
    "myfinances-20260112T100000Z.dump.age" \
    "myfinances-20260113T100000Z.dump.age" \
    "myfinances-20260114T100000Z.dump.age" \
    "myfinances-20260115T100000Z.dump.age" \
    "myfinances-20260116T100000Z.dump.age" \
    "myfinances-20260117T100000Z.dump.age" \
    "myfinances-20260118T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "myfinances-20260105T100000Z.dump.age" ]
}

@test "ISO week bucketing does not mis-sort a calendar-year boundary" {
  # 2024-12-29 (Sun) is ISO week 2024-W52; 2024-12-30 (Mon) is ISO week 2025-W01 - one calendar
  # day apart but a different ISO week-year. Both are recent enough here to be kept by the daily
  # rule regardless (only 2 day-buckets exist), so this is primarily a crash/sanity check that the
  # %G (ISO week-year) based bucket key does not error or wrap incorrectly across the boundary -
  # a %Y-based key would instead compute "2024-W01" for Dec 30, sorting *before* "2024-W52" even
  # though it is chronologically later; the decisive ranking case lives in the monthly-truncation
  # test below, which exercises the same kind of boundary at a bucket size large enough to matter.
  run_prune "2025-01-15T00:00:00Z" \
    "myfinances-20241229T120000Z.dump.age" \
    "myfinances-20241230T120000Z.dump.age"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "monthly bucket keeps only the 12 most recent months, dropping the 13th-oldest" {
  # One file on the 1st of each of 13 consecutive months. Each month is also its own day- and
  # week-bucket (spaced well over a week apart), so only the monthly rule's top-12 cutoff decides
  # the oldest (2025-01) - everything from 2025-02 on is within the most recent 12 month buckets.
  run_prune "2026-02-01T00:00:00Z" \
    "myfinances-20250101T100000Z.dump.age" \
    "myfinances-20250201T100000Z.dump.age" \
    "myfinances-20250301T100000Z.dump.age" \
    "myfinances-20250401T100000Z.dump.age" \
    "myfinances-20250501T100000Z.dump.age" \
    "myfinances-20250601T100000Z.dump.age" \
    "myfinances-20250701T100000Z.dump.age" \
    "myfinances-20250801T100000Z.dump.age" \
    "myfinances-20250901T100000Z.dump.age" \
    "myfinances-20251001T100000Z.dump.age" \
    "myfinances-20251101T100000Z.dump.age" \
    "myfinances-20251201T100000Z.dump.age" \
    "myfinances-20260101T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "myfinances-20250101T100000Z.dump.age" ]
}

@test "sparse, on-demand backups with long gaps are never pruned for having gaps" {
  # One backup roughly every two months - fewer than 7 day-buckets ever exist, so nothing should
  # be pruned purely because of the gaps between them.
  run_prune "2026-11-01T00:00:00Z" \
    "myfinances-20260101T100000Z.dump.age" \
    "myfinances-20260301T100000Z.dump.age" \
    "myfinances-20260501T100000Z.dump.age" \
    "myfinances-20260701T100000Z.dump.age" \
    "myfinances-20260901T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "a pre-upgrade file with nothing newer is exempt (and well-formed filenames parse)" {
  run_prune "$NOW" "myfinances-20260101T100000Z.pre-upgrade.dump.age"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "pre-upgrade exemption is released once later normal backups bury it past GFS retention" {
  # Same 13-consecutive-months dataset as the monthly-truncation case above, except the oldest
  # file (which ordinary GFS alone would already drop) is now tagged pre-upgrade. Because 12 later
  # *normal* backups exist, its exemption is released, ordinary GFS rules apply exactly as before,
  # and it is pruned rather than kept forever.
  run_prune "2026-02-01T00:00:00Z" \
    "myfinances-20250101T100000Z.pre-upgrade.dump.age" \
    "myfinances-20250201T100000Z.dump.age" \
    "myfinances-20250301T100000Z.dump.age" \
    "myfinances-20250401T100000Z.dump.age" \
    "myfinances-20250501T100000Z.dump.age" \
    "myfinances-20250601T100000Z.dump.age" \
    "myfinances-20250701T100000Z.dump.age" \
    "myfinances-20250801T100000Z.dump.age" \
    "myfinances-20250901T100000Z.dump.age" \
    "myfinances-20251001T100000Z.dump.age" \
    "myfinances-20251101T100000Z.dump.age" \
    "myfinances-20251201T100000Z.dump.age" \
    "myfinances-20260101T100000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "myfinances-20250101T100000Z.pre-upgrade.dump.age" ]
}

@test "running prune twice on its own surviving output deletes nothing further (idempotence)" {
  local files=(
    "myfinances-20260302T100000Z.dump.age"
    "myfinances-20260303T100000Z.dump.age"
    "myfinances-20260304T100000Z.dump.age"
    "myfinances-20260305T100000Z.dump.age"
    "myfinances-20260306T100000Z.dump.age"
    "myfinances-20260307T100000Z.dump.age"
    "myfinances-20260308T100000Z.dump.age"
    "myfinances-20260309T100000Z.dump.age"
    "myfinances-20260310T100000Z.dump.age"
    "myfinances-20260311T100000Z.dump.age"
  )
  run_prune "2026-03-15T00:00:00Z" "${files[@]}"
  [ "$status" -eq 0 ]
  local -a deleted=()
  if [ -n "$output" ]; then
    while IFS= read -r line; do deleted+=("$line"); done <<<"$output"
  fi
  local -a remaining=()
  for f in "${files[@]}"; do
    local keep=1
    for d in "${deleted[@]:-}"; do
      [ "$f" = "$d" ] && keep=0
    done
    [ "$keep" -eq 1 ] && remaining+=("$f")
  done
  run_prune "2026-03-15T00:00:00Z" "${remaining[@]}"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "files that don't match the naming pattern are never considered for deletion" {
  run_prune "$NOW" \
    "notes.txt" \
    "status.json" \
    "pre-restore-20260101T000000Z.dump" \
    "myfinances-20260310T080000Z.dump.age" \
    "myfinances-20260310T200000Z.dump.age"
  [ "$status" -eq 0 ]
  [ "$output" = "myfinances-20260310T080000Z.dump.age" ]
}

@test "a file timestamped after 'now' (clock skew) is never deleted" {
  run_prune "2020-01-01T00:00:00Z" \
    "myfinances-20260310T080000Z.dump.age" \
    "myfinances-20260310T200000Z.dump.age"
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "empty input deletes nothing" {
  run bash "$SCRIPT" "$NOW" <<<""
  [ "$status" -eq 0 ]
  [ -z "$output" ]
}

@test "missing 'now' argument is a usage error" {
  run bash "$SCRIPT"
  [ "$status" -ne 0 ]
}
