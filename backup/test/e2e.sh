#!/usr/bin/env bash
# e2e.sh — scripted Docker end-to-end test for the F018 backup sidecar (spec "Testing/End-to-end",
# plan.md Phase 2). Builds the real backup image, seeds a throwaway Postgres, takes a real
# encrypted backup, restores it into a second fresh Postgres, and compares row counts and
# flyway_schema_history. Also proves a wrong key fails cleanly (nothing touched) and that the
# pre-restore safety dump is written. Run from the repo root:
#
#   bash backup/test/e2e.sh
#
# Needs Docker. Builds and removes its own network/containers/image; safe to re-run.

set -uo pipefail

# On Git Bash for Windows, the default path-conversion heuristic mangles the container-side half
# of a `-v host:container` bind mount (e.g. turning the literal in-container path "/backups" into
# a host path under the Git installation directory) - this disables that heuristic. It is unset/
# irrelevant on Linux (CI), where the Docker CLI needs no such translation to begin with.
export MSYS_NO_PATHCONV=1

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
NETWORK="myfinances-backup-e2e-test"
SRC_PG="myfinances-backup-e2e-test-src"
DST_PG="myfinances-backup-e2e-test-dst"
IMAGE="myfinances-backup-e2e-test:latest"
# Deliberately not a bare `mktemp -d`: under some shells on Windows that resolves under the Git
# installation's own internal /tmp, which Docker Desktop's file sharing does not see - a bind
# mount against it silently mounts an empty directory instead of erroring. A path under the repo
# checkout is always on a real, shareable drive.
WORKDIR="$REPO_ROOT/backup/.e2e-tmp-$$"
rm -rf "$WORKDIR"
BACKUPS_DIR="$WORKDIR/backups"
mkdir -p "$BACKUPS_DIR"

FAILED=0
fail() {
  echo "E2E FAIL: $*" >&2
  FAILED=1
}
pass() {
  echo "E2E ok: $*"
}

cleanup() {
  docker rm -f "$SRC_PG" "$DST_PG" >/dev/null 2>&1
  docker network rm "$NETWORK" >/dev/null 2>&1
  [ -n "${E2E_KEEP_WORKDIR:-}" ] || rm -rf "$WORKDIR"
}
trap cleanup EXIT

wait_ready() {
  local container=$1
  # pg_isready alone can report ready during the brief window of the official image's own
  # internal restart (init scripts run, then Postgres restarts once) - wait for an actual query
  # to succeed, which can't happen during that restart.
  for _ in $(seq 1 60); do
    docker exec "$container" psql -U myfinances -d myfinances -tAc "select 1" >/dev/null 2>&1 && return 0
    sleep 1
  done
  return 1
}

echo "== building backup image =="
docker build --build-arg BUILD_ID=e2e-test -t "$IMAGE" -f backup/Dockerfile backup || {
  fail "image build"
  exit 1
}

echo "== generating a throwaway age keypair =="
KEYGEN_OUT=$(docker run --rm "$IMAGE" backup-keygen)
RECIPIENT=$(printf '%s\n' "$KEYGEN_OUT" | grep -o 'age1[0-9a-z]*' | head -n1)
PRIVATE_KEY=$(printf '%s\n' "$KEYGEN_OUT" | grep -o 'AGE-SECRET-KEY-[0-9A-Z]*' | head -n1)
[ -n "$RECIPIENT" ] && [ -n "$PRIVATE_KEY" ] || {
  fail "could not parse a keypair out of backup-keygen's output"
  exit 1
}
printf '%s' "$PRIVATE_KEY" >"$WORKDIR/age-key.txt"

echo "== generating a second, unrelated keypair (for the wrong-key case) =="
WRONG_KEYGEN_OUT=$(docker run --rm "$IMAGE" backup-keygen)
WRONG_PRIVATE_KEY=$(printf '%s\n' "$WRONG_KEYGEN_OUT" | grep -o 'AGE-SECRET-KEY-[0-9A-Z]*' | head -n1)
printf '%s' "$WRONG_PRIVATE_KEY" >"$WORKDIR/wrong-age-key.txt"

docker network create "$NETWORK" >/dev/null

echo "== starting + seeding the source Postgres =="
docker rm -f "$SRC_PG" >/dev/null 2>&1
docker run -d --name "$SRC_PG" --network "$NETWORK" \
  -e POSTGRES_DB=myfinances -e POSTGRES_USER=myfinances -e POSTGRES_PASSWORD=myfinances \
  postgres:17-alpine >/dev/null
wait_ready "$SRC_PG" || {
  fail "source Postgres never became ready"
  exit 1
}
docker exec "$SRC_PG" psql -U myfinances -d myfinances -v ON_ERROR_STOP=1 -c "
  CREATE TABLE flyway_schema_history (installed_rank int, version text, installed_on timestamp default now());
  INSERT INTO flyway_schema_history (installed_rank, version) VALUES (1,'1'), (2,'2'), (3,'3');
  CREATE TABLE widgets (id serial primary key, name text);
  INSERT INTO widgets (name) SELECT 'widget-' || g FROM generate_series(1,37) g;
" >/dev/null

SRC_WIDGETS=$(docker exec "$SRC_PG" psql -U myfinances -d myfinances -tAc "select count(*) from widgets;")
SRC_FLYWAY=$(docker exec "$SRC_PG" psql -U myfinances -d myfinances -tAc "select count(*) from flyway_schema_history;")

echo "== running backup.sh against the source Postgres =="
docker run --rm \
  --network "$NETWORK" \
  -e PGHOST="$SRC_PG" -e PGPORT=5432 -e PGDATABASE=myfinances -e PGUSER=myfinances -e PGPASSWORD=myfinances \
  -e BACKUP_AGE_RECIPIENT="$RECIPIENT" -e IMAGE_TAG=e2e-test \
  -v "$BACKUPS_DIR:/backups" \
  "$IMAGE" backup.sh || fail "backup.sh exited non-zero"

BACKUP_FILE=$(find "$BACKUPS_DIR" -maxdepth 1 -name 'myfinances-*.dump.age' ! -name '*.pre-upgrade.*' | head -n1)
[ -n "$BACKUP_FILE" ] && pass "backup file written ($BACKUP_FILE)" || fail "no backup file was written"

if [ -f "$BACKUPS_DIR/status.json" ]; then
  LAST_ERROR=$(docker run --rm -v "$BACKUPS_DIR:/backups" "$IMAGE" jq -r '.lastError' /backups/status.json 2>/dev/null)
  if [ "$LAST_ERROR" = "null" ]; then
    pass "status.json reports no error"
  else
    fail "status.json reports lastError=$LAST_ERROR"
  fi
else
  fail "status.json was not written"
fi

echo "== starting the destination (fresh) Postgres =="
docker rm -f "$DST_PG" >/dev/null 2>&1
docker run -d --name "$DST_PG" --network "$NETWORK" \
  -e POSTGRES_DB=myfinances -e POSTGRES_USER=myfinances -e POSTGRES_PASSWORD=myfinances \
  postgres:17-alpine >/dev/null
wait_ready "$DST_PG" || {
  fail "destination Postgres never became ready"
  exit 1
}

echo "== wrong-key restore must fail cleanly, nothing touched =="
BACKUP_NAME=$(basename "$BACKUP_FILE")
WRONG_KEY_OUTPUT=$(docker run --rm \
  --network "$NETWORK" \
  -e PGHOST="$DST_PG" -e PGPORT=5432 -e PGDATABASE=myfinances -e PGUSER=myfinances -e PGPASSWORD=myfinances \
  -e AGE_KEY_FILE=/run/secrets/age-key.txt \
  -v "$BACKUPS_DIR:/backups" \
  -v "$WORKDIR/wrong-age-key.txt:/run/secrets/age-key.txt:ro" \
  "$IMAGE" restore.sh "$BACKUP_NAME" --yes 2>&1)
WRONG_KEY_STATUS=$?
if [ "$WRONG_KEY_STATUS" -ne 0 ] && printf '%s' "$WRONG_KEY_OUTPUT" | grep -q "Nothing was touched"; then
  pass "wrong key fails cleanly before touching the database"
else
  fail "wrong key did not fail the way it should have: $WRONG_KEY_OUTPUT"
fi
DST_TABLE_COUNT_AFTER_WRONG_KEY=$(docker exec "$DST_PG" psql -U myfinances -d myfinances -tAc \
  "select count(*) from information_schema.tables where table_schema='public';")
[ "$DST_TABLE_COUNT_AFTER_WRONG_KEY" -eq 0 ] && pass "destination still has no tables after the wrong-key attempt" ||
  fail "destination unexpectedly has tables after a failed wrong-key restore"

echo "== restoring with the correct key =="
docker run --rm \
  --network "$NETWORK" \
  -e PGHOST="$DST_PG" -e PGPORT=5432 -e PGDATABASE=myfinances -e PGUSER=myfinances -e PGPASSWORD=myfinances \
  -e AGE_KEY_FILE=/run/secrets/age-key.txt -e BACKUP_AGE_RECIPIENT="$RECIPIENT" \
  -v "$BACKUPS_DIR:/backups" \
  -v "$WORKDIR/age-key.txt:/run/secrets/age-key.txt:ro" \
  "$IMAGE" restore.sh "$BACKUP_NAME" --yes || fail "restore.sh (correct key) exited non-zero"

DST_WIDGETS=$(docker exec "$DST_PG" psql -U myfinances -d myfinances -tAc "select count(*) from widgets;")
DST_FLYWAY=$(docker exec "$DST_PG" psql -U myfinances -d myfinances -tAc "select count(*) from flyway_schema_history;")

[ "$SRC_WIDGETS" = "$DST_WIDGETS" ] && pass "widgets row count matches ($DST_WIDGETS)" ||
  fail "widgets row count mismatch: source=$SRC_WIDGETS destination=$DST_WIDGETS"
[ "$SRC_FLYWAY" = "$DST_FLYWAY" ] && pass "flyway_schema_history row count matches ($DST_FLYWAY)" ||
  fail "flyway_schema_history row count mismatch: source=$SRC_FLYWAY destination=$DST_FLYWAY"

SAFETY_DUMP=$(find "$BACKUPS_DIR" -maxdepth 1 -name 'pre-restore-*' | head -n1)
[ -n "$SAFETY_DUMP" ] && pass "pre-restore safety dump exists ($(basename "$SAFETY_DUMP"))" ||
  fail "no pre-restore safety dump was written"

if [ "$FAILED" -eq 0 ]; then
  echo "E2E: all checks passed"
  exit 0
else
  echo "E2E: one or more checks failed" >&2
  exit 1
fi
