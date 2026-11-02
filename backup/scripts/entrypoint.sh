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
GRACE_SECONDS="${BACKUP_STORAGE_GRACE_SECONDS:-300}"
# When storage.sh first found the folder volatile, in /tmp: it survives a restart of the container
# (what the restart policy does) and not a recreate, which is exactly the retry window we want.
VOLATILE_SINCE_FILE="${BACKUP_VOLATILE_SINCE_FILE:-/tmp/backup-volatile-since}"

log() { echo "[entrypoint.sh] $*"; }

# The container starts as root (no `user:` override in compose) so this can run; a fresh
# `docker volume create`d named volume - and most first-time bind mounts - is root:root, which a
# fixed non-root `user:` could never write to no matter what BACKUP_UID/BACKUP_GID said. Fix
# ownership once here, then re-exec the rest of this script as the unprivileged target uid/gid via
# `gosu` (Alpine's package, installed in backup/Dockerfile). A container already
# started non-root (someone overrode `user:` back) skips this and runs as-is.
if [ "$(id -u)" = "0" ]; then
  TARGET_UID="${BACKUP_UID:-1000}"
  TARGET_GID="${BACKUP_GID:-1000}"
  mkdir -p "$STORAGE_DIR"
  chown "$TARGET_UID:$TARGET_GID" "$STORAGE_DIR" 2>/dev/null || true
  exec gosu "$TARGET_UID:$TARGET_GID" "$0" "$@"
fi

# Issue #239, ADR 0015: after a Docker Desktop restart a bind mount can come up as an in-memory
# directory because its drive is not mounted yet. A restart of this container resolves the bind
# again, so while the folder is volatile this exits non-zero and the restart policy retries; the
# retry that lands after the drive is mounted gets the real folder. Only for GRACE_SECONDS from the
# first detection: after that the sidecar starts anyway (the backend waits for it, and a typo in
# BACKUP_DIR must not keep the whole app down). backup.sh refuses to take a backup there.
check_storage_at_start() {
  local verdict now since elapsed
  if verdict=$("$SCRIPT_DIR/storage.sh" "$STORAGE_DIR"); then
    rm -f "$VOLATILE_SINCE_FILE"
    return 0
  fi
  now=$(date +%s)
  since=$(cat "$VOLATILE_SINCE_FILE" 2>/dev/null || true)
  if ! [[ $since =~ ^[0-9]+$ ]]; then
    since=$now
    echo "$now" >"$VOLATILE_SINCE_FILE"
  fi
  elapsed=$((now - since))
  if [ "$elapsed" -lt "$GRACE_SECONDS" ]; then
    log "storage is volatile (${verdict#volatile }): exiting so the restart policy retries ($((GRACE_SECONDS - elapsed))s of grace left)"
    exit 1
  fi
  log "ERROR storage is volatile (${verdict#volatile }) after ${elapsed}s: starting anyway so the app is not kept down; no backup will be taken until this container is restarted on a real folder"
}

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

check_storage_at_start

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
