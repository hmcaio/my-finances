#!/usr/bin/env bash
# entrypoint.sh — the sidecar's long-running process (F018 spec "Sidecar/Scripts", ADR 0015).
#
# On start: read the marker, run decide.sh (forced/stale/fresh) against this image's own build id,
# run backup.sh when forced or stale (tagging the result .pre-upgrade when forced), then mark the
# healthcheck ready - whether that backup succeeded or failed, so a failing backup never blocks
# the rest of the stack from starting (ADR 0015). Then loop hourly, repeating the same decision
# and backup-if-needed step while the stack is up.

set -uo pipefail

STORAGE_DIR="${BACKUP_STORAGE_DIR:-/backups}"
MARKER="$STORAGE_DIR/status.json"
BUILD_ID_FILE="${BACKUP_BUILD_ID_FILE:-/etc/backup-build-id}"
HEALTH_FILE="${BACKUP_HEALTH_FILE:-/tmp/backup-healthcheck-ready}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAX_AGE_HOURS="${BACKUP_MAX_AGE_HOURS:-20}"

log() { echo "[entrypoint.sh] $*"; }

check_and_backup_if_needed() {
  local build_id now marker_json decision
  build_id=$(cat "$BUILD_ID_FILE" 2>/dev/null || echo "")
  now=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  marker_json=""
  [ -f "$MARKER" ] && marker_json=$(cat "$MARKER")

  decision=$(printf '%s' "$marker_json" | "$SCRIPT_DIR/decide.sh" "$build_id" "$MAX_AGE_HOURS" "$now")
  log "decision: $decision"

  case "$decision" in
  forced)
    "$SCRIPT_DIR/backup.sh" --pre-upgrade
    ;;
  stale)
    "$SCRIPT_DIR/backup.sh"
    ;;
  fresh)
    : # nothing to do
    ;;
  *)
    log "unexpected decision '$decision', treating as stale"
    "$SCRIPT_DIR/backup.sh"
    ;;
  esac
}

mkdir -p "$STORAGE_DIR"

log "start-time check"
check_and_backup_if_needed || true

# Healthy once the start-time check has finished, success or failure - a failing backup must
# never block the rest of the stack from starting (ADR 0015, "A failed backup never blocks the
# app"). The compose healthcheck just tests for this file's existence.
touch "$HEALTH_FILE"
log "ready"

while true; do
  sleep 3600
  log "hourly check"
  check_and_backup_if_needed || true
done
