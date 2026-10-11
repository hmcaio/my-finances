#!/usr/bin/env bash
# restore.sh <file> --yes — restore the database from an encrypted backup (F018 spec "Sidecar/
# Restore", ADR 0015). Run as a one-off container:
#
#   docker compose -f docker-compose.prod.yml run --rm --no-deps backup restore.sh <file> --yes
#
# <file> is a name inside the mounted backup folder (/backups by default), e.g.
# myfinances-20260310T080000Z.dump.age. The private age key is supplied at run time, never stored
# in .env - mount it read-only and point AGE_KEY_FILE at it, e.g.:
#
#   docker compose -f docker-compose.prod.yml run --rm --no-deps \
#     -v /path/to/age-key.txt:/run/secrets/age-key.txt:ro \
#     -e AGE_KEY_FILE=/run/secrets/age-key.txt \
#     backup restore.sh myfinances-20260310T080000Z.dump.age --yes
#
# Preflight (decrypt + pg_restore --list) runs before anything is touched, so a wrong key or a
# corrupt file fails cleanly with the database untouched. A pre-restore-<timestamp>.dump.age
# safety dump of the *current* database is always taken first, encrypted with the same
# BACKUP_AGE_RECIPIENT as ordinary backups - its name deliberately does not match the
# myfinances-*.dump.age pattern prune.sh prunes, so it is never auto-deleted.
#
# This script only handles the database itself. Stopping the backend/frontend containers before
# running it and starting them again afterwards is a host-level docker compose step (see the
# README) - the backup container is deliberately never given the Docker socket (same reasoning
# ADR 0015 already gives for not reading the backend image's digest: it is root-equivalent access
# to the host).

set -uo pipefail

STORAGE_DIR="${BACKUP_STORAGE_DIR:-/backups}"
AGE_KEY_FILE="${AGE_KEY_FILE:-/run/secrets/age-key.txt}"

FILE="${1:-}"
CONFIRM="${2:-}"

if [ -z "$FILE" ] || [ "$CONFIRM" != "--yes" ]; then
  echo "usage: restore.sh <file-in-$STORAGE_DIR> --yes" >&2
  exit 2
fi

BACKUP_PATH="$STORAGE_DIR/$FILE"
if [ ! -f "$BACKUP_PATH" ]; then
  echo "restore.sh: $BACKUP_PATH not found" >&2
  exit 1
fi

if [ ! -r "$AGE_KEY_FILE" ]; then
  echo "restore.sh: private key file not readable at $AGE_KEY_FILE (set AGE_KEY_FILE / mount it)" >&2
  exit 1
fi

PREFLIGHT_DUMP="/tmp/restore-preflight-$$.dump"
cleanup() { rm -f "$PREFLIGHT_DUMP"; }
trap cleanup EXIT

echo "restore.sh: decrypting $FILE for preflight..."
if ! age -d -i "$AGE_KEY_FILE" -o "$PREFLIGHT_DUMP" "$BACKUP_PATH" 2>/tmp/restore-age.err; then
  echo "restore.sh: cannot decrypt $FILE - wrong key or corrupt file. Nothing was touched." >&2
  exit 1
fi

echo "restore.sh: validating dump contents with pg_restore --list..."
if ! pg_restore --list "$PREFLIGHT_DUMP" >/dev/null 2>/tmp/restore-list.err; then
  echo "restore.sh: pg_restore cannot read the decrypted dump. Nothing was touched." >&2
  exit 1
fi
echo "restore.sh: preflight ok."

if [ -z "${PGDATABASE:-}" ]; then
  echo "restore.sh: PGDATABASE is not set" >&2
  exit 1
fi

TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
SAFETY_NAME="pre-restore-${TIMESTAMP}.dump.age"
SAFETY_TMP="$STORAGE_DIR/.tmp-safety-$$.dump"

echo "restore.sh: taking a pre-restore safety dump of the current database..."
if ! pg_dump -Fc -f "$SAFETY_TMP"; then
  echo "restore.sh: could not take the pre-restore safety dump - aborting before touching anything." >&2
  rm -f "$SAFETY_TMP"
  exit 1
fi

if [ -n "${BACKUP_AGE_RECIPIENT:-}" ]; then
  if ! age -r "$BACKUP_AGE_RECIPIENT" -o "$STORAGE_DIR/$SAFETY_NAME" "$SAFETY_TMP"; then
    echo "restore.sh: could not encrypt the safety dump - aborting before touching anything." >&2
    rm -f "$SAFETY_TMP"
    exit 1
  fi
  rm -f "$SAFETY_TMP"
else
  # No recipient configured (e.g. a dev/test run) - keep the safety dump unencrypted rather than
  # fail the whole restore over it, but say so loudly.
  mv "$SAFETY_TMP" "$STORAGE_DIR/pre-restore-${TIMESTAMP}.dump"
  echo "restore.sh: BACKUP_AGE_RECIPIENT not set - safety dump written unencrypted."
fi
echo "restore.sh: safety dump written ($SAFETY_NAME)."

echo "restore.sh: terminating other connections to $PGDATABASE..."
psql -d postgres -v ON_ERROR_STOP=1 -c \
  "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$PGDATABASE';" \
  >/dev/null 2>&1 || true

echo "restore.sh: dropping and recreating $PGDATABASE..."
if ! psql -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS \"$PGDATABASE\";"; then
  echo "restore.sh: could not drop the database. The pre-restore safety dump is at $SAFETY_NAME - the original database may still be intact." >&2
  exit 1
fi
if ! psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE \"$PGDATABASE\" OWNER \"$PGUSER\";"; then
  echo "restore.sh: could not recreate the database after dropping it. Restore from $SAFETY_NAME immediately." >&2
  exit 1
fi

echo "restore.sh: running pg_restore..."
if ! pg_restore -d "$PGDATABASE" "$PREFLIGHT_DUMP"; then
  echo "restore.sh: pg_restore reported errors. The database may be partially restored - restore from $SAFETY_NAME if needed." >&2
  exit 1
fi

echo "restore.sh: done. Restart the backend and frontend containers now."
exit 0
