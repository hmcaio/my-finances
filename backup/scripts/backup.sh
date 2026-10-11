#!/usr/bin/env bash
# backup.sh — one backup run (F018 spec "Sidecar/Scripts", ADR 0015).
#
#   pg_dump -Fc -> age encrypt -> temp file -> atomic rename -> optional rclone push -> prune ->
#   update status.json
#
# Exits non-zero on any failure, leaving every previously existing backup file untouched - a
# failure before the final rename never creates a partial file in the backup folder at all, and a
# failure after it (an rclone push) never deletes the local file that already succeeded. Refuses
# to run at all without BACKUP_AGE_RECIPIENT. Logs file names and counts only, never row data
# (ADR 0011) - pg_dump's own stderr is not echoed, only "ok"/"failed" and a short reason.
#
# Usage: backup.sh [--pre-upgrade]
#
# Reads from the environment: BACKUP_AGE_RECIPIENT (required), BACKUP_DIR (the *value* configured
# on the host - just used to tell a bind mount from a named volume for status.json's targetType,
# the mount itself is always at $BACKUP_STORAGE_DIR), BACKUP_RCLONE_REMOTE (optional),
# BACKUP_STORAGE_DIR (default /backups), PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD (standard
# libpq vars, consumed by pg_dump directly), IMAGE_TAG (optional, for status.json).

set -uo pipefail

STORAGE_DIR="${BACKUP_STORAGE_DIR:-/backups}"
MARKER="$STORAGE_DIR/status.json"
BUILD_ID_FILE="${BACKUP_BUILD_ID_FILE:-/etc/backup-build-id}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PRE_UPGRADE=0
if [ "${1:-}" = "--pre-upgrade" ]; then
  PRE_UPGRADE=1
fi

log() {
  # ids/counts/state only - never a file's content, a path's data, or a raw tool error message
  # (which could echo a connection string or similar).
  echo "[backup.sh] $*"
}

target_type() {
  case "${BACKUP_DIR:-}" in
  */* | *\\*) echo "bind" ;;
  *) echo "volume" ;;
  esac
}

# Reads the existing marker's given field (jq path, e.g. ".lastSuccessAt"), or "" if the marker is
# missing/unreadable - used to preserve lastSuccessAt across a failed attempt.
read_marker_field() {
  local field=$1
  [ -f "$MARKER" ] || return 0
  jq -r "$field // \"\"" "$MARKER" 2>/dev/null || true
}

# Atomically writes status.json (merge semantics: preserves lastSuccessAt on failure).
# write_status <ok|fail> <error-category-or-empty> <count>
write_status() {
  local ok=$1 error=$2 count=$3
  local now
  now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  local last_success
  if [ "$ok" = "ok" ]; then
    last_success="$now"
  else
    last_success=$(read_marker_field '.lastSuccessAt')
  fi
  local build_id
  build_id=$(cat "$BUILD_ID_FILE" 2>/dev/null || echo "")
  local schema_version
  schema_version=$(psql -tA -c "select version from flyway_schema_history order by installed_rank desc limit 1" 2>/dev/null | tr -d '[:space:]')
  local remote_configured="false"
  [ -n "${BACKUP_RCLONE_REMOTE:-}" ] && remote_configured="true"

  local tmp="$MARKER.tmp.$$"
  jq -n \
    --arg lastSuccessAt "$last_success" \
    --arg lastAttemptAt "$now" \
    --arg lastError "$error" \
    --arg imageTag "${IMAGE_TAG:-}" \
    --arg buildId "$build_id" \
    --arg schemaVersion "$schema_version" \
    --arg targetType "$(target_type)" \
    --argjson remoteConfigured "$remote_configured" \
    --argjson remoteOk "${REMOTE_OK_JSON:-null}" \
    --argjson count "$count" \
    '{
      lastSuccessAt: (if $lastSuccessAt == "" then null else $lastSuccessAt end),
      lastAttemptAt: $lastAttemptAt,
      lastError: (if $lastError == "" then null else $lastError end),
      imageTag: $imageTag,
      buildId: $buildId,
      schemaVersion: (if $schemaVersion == "" then null else $schemaVersion end),
      targetType: $targetType,
      remoteConfigured: $remoteConfigured,
      remoteOk: $remoteOk,
      count: $count
    }' >"$tmp" && mv -f "$tmp" "$MARKER"
}

count_backups() {
  find "$STORAGE_DIR" -maxdepth 1 -type f -name 'myfinances-*.dump.age' 2>/dev/null | wc -l | tr -d '[:space:]'
}

fail() {
  local category=$1
  log "failed: $category"
  write_status fail "$category" "$(count_backups)"
  exit 1
}

if [ -z "${BACKUP_AGE_RECIPIENT:-}" ]; then
  log "refusing to run: BACKUP_AGE_RECIPIENT is not set"
  # Nothing to preserve/compute yet (no DB connection attempted) - write a minimal failure marker
  # directly rather than through write_status, which would also try (and fail) a DB query.
  now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  tmp="$MARKER.tmp.$$"
  jq -n --arg now "$now" --arg lastSuccessAt "$(read_marker_field '.lastSuccessAt')" \
    '{lastSuccessAt: (if $lastSuccessAt == "" then null else $lastSuccessAt end),
      lastAttemptAt: $now, lastError: "missing_recipient", imageTag: null, buildId: null,
      schemaVersion: null, targetType: null, remoteConfigured: false, remoteOk: null, count: 0}' \
    >"$tmp" 2>/dev/null && mv -f "$tmp" "$MARKER" 2>/dev/null
  exit 1
fi

mkdir -p "$STORAGE_DIR"

NOW_EPOCH=$(date -u +%s)
NOW_COMPACT=$(date -u -d "@$NOW_EPOCH" +%Y%m%dT%H%M%SZ)
SUFFIX=""
[ "$PRE_UPGRADE" -eq 1 ] && SUFFIX=".pre-upgrade"
FINAL_NAME="myfinances-${NOW_COMPACT}${SUFFIX}.dump.age"

TMP_DUMP="$STORAGE_DIR/.tmp-$$.dump"
TMP_ENC="$STORAGE_DIR/.tmp-$$.dump.age"
cleanup() { rm -f "$TMP_DUMP" "$TMP_ENC"; }
trap cleanup EXIT

log "starting backup (pre-upgrade=$PRE_UPGRADE)"

if ! pg_dump -Fc -f "$TMP_DUMP" 2>/tmp/pg_dump.err; then
  fail "dump_failed"
fi

if ! age -r "$BACKUP_AGE_RECIPIENT" -o "$TMP_ENC" "$TMP_DUMP" 2>/tmp/age.err; then
  fail "encrypt_failed"
fi
rm -f "$TMP_DUMP"

if ! mv -f "$TMP_ENC" "$STORAGE_DIR/$FINAL_NAME"; then
  fail "rename_failed"
fi
log "wrote $FINAL_NAME"

REMOTE_OK_JSON="null"
REMOTE_FAILED=0
if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
  if rclone copy "$STORAGE_DIR/$FINAL_NAME" "$BACKUP_RCLONE_REMOTE" 2>/tmp/rclone.err; then
    REMOTE_OK_JSON="true"
    log "pushed $FINAL_NAME to remote"
  else
    REMOTE_OK_JSON="false"
    REMOTE_FAILED=1
    log "remote push failed"
  fi
fi

# Prune (local folder). GFS + pre-upgrade logic lives entirely in prune.sh; this only acts on its
# output, which never includes a file that doesn't match the naming pattern.
NOW_ISO=$(date -u -d "@$NOW_EPOCH" +%Y-%m-%dT%H:%M:%SZ)
mapfile -t TO_DELETE < <(find "$STORAGE_DIR" -maxdepth 1 -type f -name 'myfinances-*.dump.age' -printf '%f\n' 2>/dev/null | "$SCRIPT_DIR/prune.sh" "$NOW_ISO")
for f in "${TO_DELETE[@]:-}"; do
  [ -z "$f" ] && continue
  rm -f "$STORAGE_DIR/$f"
  log "pruned $f"
done
log "pruned $(( ${#TO_DELETE[@]} )) file(s)"

if [ -n "${BACKUP_RCLONE_REMOTE:-}" ]; then
  mapfile -t REMOTE_LIST < <(rclone lsf "$BACKUP_RCLONE_REMOTE" 2>/dev/null || true)
  if [ "${#REMOTE_LIST[@]}" -gt 0 ]; then
    mapfile -t REMOTE_TO_DELETE < <(printf '%s\n' "${REMOTE_LIST[@]}" | "$SCRIPT_DIR/prune.sh" "$NOW_ISO")
    for f in "${REMOTE_TO_DELETE[@]:-}"; do
      [ -z "$f" ] && continue
      rclone deletefile "$BACKUP_RCLONE_REMOTE/$f" 2>/dev/null && log "pruned $f from remote"
    done
  fi
fi

COUNT=$(count_backups)
if [ "$REMOTE_FAILED" -eq 1 ]; then
  write_status fail "remote_push_failed" "$COUNT"
  log "backup finished with a failure: remote_push_failed"
  exit 1
fi

write_status ok "" "$COUNT"
log "backup finished ok ($COUNT file(s) retained)"
exit 0
