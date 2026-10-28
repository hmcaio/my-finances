#!/usr/bin/env bash
# Re-runs the issue #189 benchmark on a disposable setup and tears it down afterwards. See
# docs/conventions/performance.md for what each part measures and how to read it.
#
#   scripts/bench/run.sh [--sizes "1000 5000 25000"] [--ttfc] [--baseline <git-ref>] [--keep]
#
#   --sizes     ledger row counts to seed and measure, one after the other
#   --ttfc      also build the frontend and time first content in headless Chromium (needs the
#               frontend's node_modules and `npx playwright install chromium`)
#   --baseline  also build <git-ref> (a commit, tag or branch) as a second backend on the same
#               database, time it the same way and compare every response byte for byte
#   --keep      keep the scratch directory (logs, built jars) instead of deleting it
#
# Needs Docker, Node, a JDK (the toolchain in backend/build.gradle) and a free local port per
# service; it picks free ports and never touches the dev or prod stack. It builds into a scratch
# directory through backend/gradle/relocate-build-dir.init.gradle, so a root-owned backend/build
# does not get in the way.
set -euo pipefail
cd "$(dirname "$0")/../.."
REPO=$PWD
BENCH=$REPO/scripts/bench
SIZES="1000 5000 25000"
TTFC=0
BASELINE=""
KEEP=0
while [ $# -gt 0 ]; do
  case $1 in
    --sizes) SIZES=$2; shift 2 ;;
    --ttfc) TTFC=1; shift ;;
    --baseline) BASELINE=$2; shift 2 ;;
    --keep) KEEP=1; shift ;;
    -h | --help) sed -n '2,19p' "$0"; exit 0 ;;
    *) echo "unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
done
for tool in docker node java git; do
  command -v "$tool" >/dev/null || { echo "bench: '$tool' is required" >&2; exit 2; }
done
if [ "$TTFC" = 1 ] && [ ! -d frontend/node_modules ]; then
  echo "bench: --ttfc needs frontend/node_modules (run 'npm ci' in frontend/)" >&2
  exit 2
fi

SCRATCH=$(mktemp -d)
PIDS=()
CONTAINER=""
WORKTREE=""

cleanup() {
  set +e
  # By PID, never `pkill -f <pattern>`: a pattern can match this very shell and abort the cleanup.
  for pid in ${PIDS[@]+"${PIDS[@]}"}; do kill "$pid" 2>/dev/null; done
  [ -n "$CONTAINER" ] && docker rm -fv "$CONTAINER" >/dev/null 2>&1
  if [ -n "$WORKTREE" ]; then git worktree remove --force "$WORKTREE" >/dev/null 2>&1; git worktree prune; fi
  if [ "$KEEP" = 1 ]; then echo "bench: kept $SCRATCH"; else rm -rf "$SCRATCH"; fi
}
trap cleanup EXIT

free_port() {
  node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"
}

wait_for_http() { # url, seconds, description
  local i
  for i in $(seq 1 "$2"); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$1" || true)" = 401 ] && return 0
    sleep 1
  done
  echo "bench: $3 did not come up (see $SCRATCH)" >&2
  KEEP=1
  return 1
}

start_backend() { # jar, port, label
  SPRING_PROFILES_ACTIVE=prod DB_HOST=localhost DB_PORT=$PGPORT DB_NAME=myfinances DB_USER=myfinances \
    DB_PASSWORD=$PW SERVER_PORT=$2 SERVER_ADDRESS=127.0.0.1 LOGGING_FILE_NAME=$SCRATCH/$3.log \
    AUTH_USERNAME=bench AUTH_PASSWORD_HASH=$HASH \
    java -jar "$1" >"$SCRATCH/$3.out" 2>&1 &
  PIDS+=($!)
  wait_for_http "http://127.0.0.1:$2/api/auth/session" 180 "the $3 backend"
}

psql_file() { docker exec -i "$CONTAINER" psql -U myfinances -d myfinances -q "$@"; }

echo "== credential and database"
PW=$(node -e "console.log(require('crypto').randomBytes(12).toString('hex'))")
HASH_LINE=$(cd backend && AUTH_PASSWORD=$PW GRADLE_BUILD_DIR=$SCRATCH/new/build \
  ./gradlew -I gradle/relocate-build-dir.init.gradle -q generateAuthHash 2>/dev/null)
HASH=${HASH_LINE#AUTH_PASSWORD_HASH=\'}
HASH=${HASH%\'}
PG_IMAGE=$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(postgres:[^[:space:]]*\).*/\1/p' docker-compose.yml | head -1)
PGPORT=$(free_port)
CONTAINER=mf-bench-pg-$$
# --tmpfs: the postgres image declares its data directory a volume, which would outlive the container.
docker run -d --name "$CONTAINER" --tmpfs /var/lib/postgresql -p "127.0.0.1:$PGPORT:5432" \
  -e POSTGRES_USER=myfinances -e POSTGRES_PASSWORD="$PW" -e POSTGRES_DB=myfinances "$PG_IMAGE" >/dev/null
until docker exec "$CONTAINER" pg_isready -U myfinances >/dev/null 2>&1; do sleep 1; done

echo "== building and starting the backend under test ($(git rev-parse --short HEAD))"
(cd backend && GRADLE_BUILD_DIR=$SCRATCH/new/build ./gradlew -I gradle/relocate-build-dir.init.gradle -q bootJar)
NEW_API_PORT=$(free_port)
start_backend "$(ls "$SCRATCH"/new/build/libs/*.jar | grep -v plain | head -1)" "$NEW_API_PORT" new
psql_file <"$BENCH/seed-base.sql"

export BENCH_PASSWORD=$PW BENCH_PG_CONTAINER=$CONTAINER BENCH_API=http://127.0.0.1:$NEW_API_PORT

if [ -n "$BASELINE" ]; then
  echo "== building and starting the baseline backend ($BASELINE)"
  WORKTREE=$SCRATCH/baseline-src
  git worktree add --detach "$WORKTREE" "$BASELINE" >/dev/null
  (cd "$WORKTREE/backend" && ./gradlew -q bootJar)
  BASE_API_PORT=$(free_port)
  start_backend "$(ls "$WORKTREE"/backend/build/libs/*.jar | grep -v plain | head -1)" "$BASE_API_PORT" baseline
  export BENCH_BASELINE_API=http://127.0.0.1:$BASE_API_PORT
fi

if [ "$TTFC" = 1 ]; then
  echo "== building the frontend"
  (cd frontend && npx vite build --outDir "$SCRATCH/dist" --emptyOutDir >/dev/null)
  FRONT_PORT=$(free_port)
  node "$BENCH/proxy.mjs" "$SCRATCH/dist" "$FRONT_PORT" "$NEW_API_PORT" &
  PIDS+=($!)
  export BENCH_FRONTEND=http://127.0.0.1:$FRONT_PORT
fi

for N in $SIZES; do
  echo
  echo "================ $N ledger rows ================"
  psql_file -v n="$N" <"$BENCH/seed-ledger.sql" | tail -3
  if [ -n "$BASELINE" ]; then
    echo "-- baseline ($BASELINE): endpoints"
    BENCH_API=$BENCH_BASELINE_API node "$BENCH/bench.mjs"
  fi
  echo "-- current: endpoints"
  node "$BENCH/bench.mjs"
  echo "-- current: SQL statements per request"
  node "$BENCH/statements.mjs"
  if [ "$TTFC" = 1 ]; then
    echo "-- current: time to first content"
    node "$BENCH/ttfc.mjs"
  fi
  if [ -n "$BASELINE" ]; then
    echo "-- baseline vs current: responses"
    node "$BENCH/compare.mjs" || STATUS=1
  fi
done
exit "${STATUS:-0}"
