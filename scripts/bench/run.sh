#!/usr/bin/env bash
# Re-runs the issue #189 benchmark on a disposable setup and tears it down afterwards. See
# docs/conventions/performance.md for what each part measures and how to read it.
#
#   scripts/bench/run.sh [--sizes "1000 5000 25000"] [--ttfc] [--baseline <git-ref>]
#                        [--image-tag <tag>] [--cpus <n>] [--fast] [--keep]
#
# By default it runs the real docker-compose.prod.yml (nginx, the production images, Postgres on a
# volume, the backup sidecar) as its own Compose project, layered with bench.override.yml, and may
# run beside a real prod stack: guard.mjs checks the resolved config before anything is created.
#
#   --sizes      ledger row counts to seed and measure, one after the other
#   --ttfc       also time first content in headless Chromium (needs frontend/node_modules and
#                `npx playwright install chromium`)
#   --baseline   also build <git-ref> (a commit, tag or branch) as a second backend on the same
#                database, time it the same way and compare every response byte for byte
#   --image-tag  measure the published images of that tag (pulled, not built) instead of building
#                this checkout; --baseline still builds from its ref
#   --cpus       cap the benchmark's backend, frontend and Postgres at <n> CPUs, to protect a prod
#                stack running beside it (prod itself has no limit, so this departs from it)
#   --fast       skip Compose: the backend jar on the host JVM, Postgres on a tmpfs and a small Node
#                proxy instead of nginx. Quicker, less like prod; needs a JDK
#   --keep       keep the scratch directory (logs, env files) instead of deleting it
#
# Needs Docker (Compose 2.24+ for the `!reset` tag) and Node; never touches the dev or prod stack.
set -euo pipefail
SELF=$(cd "$(dirname "$0")" && pwd)/$(basename "$0")
cd "$(dirname "$SELF")/../.."
BENCH=$PWD/scripts/bench
SIZES="1000 5000 25000"
TTFC=0
BASELINE=""
IMAGE_TAG_OPT=""
CPUS=""
FAST=0
KEEP=0
while [ $# -gt 0 ]; do
  case $1 in
    --sizes) SIZES=$2; shift 2 ;;
    --ttfc) TTFC=1; shift ;;
    --baseline) BASELINE=$2; shift 2 ;;
    --image-tag) IMAGE_TAG_OPT=$2; shift 2 ;;
    --cpus) CPUS=$2; shift 2 ;;
    --fast) FAST=1; shift ;;
    --keep) KEEP=1; shift ;;
    -h | --help) sed -n '2,/^[^#]/{/^[^#]/!p}' "$SELF"; exit 0 ;;
    *) echo "unknown option: $1 (see --help)" >&2; exit 2 ;;
  esac
done
need() { command -v "$1" >/dev/null || { echo "bench: '$1' is required" >&2; exit 2; }; }
for tool in docker node curl; do need "$tool"; done
[ -n "$BASELINE" ] && need git
if [ "$FAST" = 1 ]; then
  need java
  [ -z "$IMAGE_TAG_OPT$CPUS" ] || { echo "bench: --image-tag and --cpus do not apply to --fast" >&2; exit 2; }
else
  compose_version=$(docker compose version --short 2>/dev/null || true)
  [ -n "$compose_version" ] && [ "$(printf '%s\n' 2.24.0 "$compose_version" | sort -V | head -1)" = 2.24.0 ] \
    || { echo "bench: Docker Compose 2.24+ is required (found '${compose_version:-none}'), for the !reset tag" >&2; exit 2; }
fi
if [ "$TTFC" = 1 ] && [ ! -d frontend/node_modules ]; then
  echo "bench: --ttfc needs frontend/node_modules (run 'npm ci' in frontend/)" >&2
  exit 2
fi

SCRATCH=$(mktemp -d)
PIDS=()
CONTAINER=""       # --fast: the Postgres container
BASE_CONTAINER=""  # the baseline backend container (Compose mode)
BUILT_IMAGES=()    # tags this run created, and only those, are removed again
STACK_CREATED=0
WORKTREE=""
PROJECT=""
STACK=""

dc() { (cd "$STACK" && docker compose -p "$PROJECT" "${COMPOSE_FILES[@]}" "$@"); }

cleanup() {
  set +e
  # By PID, never `pkill -f <pattern>`: a pattern can match this very shell and abort the cleanup.
  for pid in ${PIDS[@]+"${PIDS[@]}"}; do kill "$pid" 2>/dev/null; done
  [ -n "$BASE_CONTAINER" ] && docker rm -fv "$BASE_CONTAINER" >/dev/null 2>&1
  # Only after guard.mjs passed (STACK_CREATED): every volume of the project is then its own.
  [ "$STACK_CREATED" = 1 ] && dc down -v --remove-orphans >/dev/null 2>&1
  [ -n "$CONTAINER" ] && docker rm -fv "$CONTAINER" >/dev/null 2>&1
  for image in ${BUILT_IMAGES[@]+"${BUILT_IMAGES[@]}"}; do docker rmi "$image" >/dev/null 2>&1; done
  if [ -n "$WORKTREE" ]; then git worktree remove --force "$WORKTREE" >/dev/null 2>&1; git worktree prune; fi
  if [ "$KEEP" = 1 ]; then echo "bench: kept $SCRATCH"; else rm -rf "$SCRATCH"; fi
}
trap cleanup EXIT

free_port() {
  node -e "const s=require('net').createServer().listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})"
}
rand() { head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'; }

wait_for_http() { # url, seconds, description, log source ("container:<name>" or "file:<path>")
  local i
  for i in $(seq 1 "$2"); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$1" || true)" = 401 ] && return 0
    sleep 1
  done
  echo "bench: $3 did not come up; its last log lines:" >&2
  case $4 in
    container:*) docker logs --tail 30 "${4#container:}" >&2 2>&1 ;;
    file:*) tail -n 30 "${4#file:}" >&2 ;;
  esac
  KEEP=1
  return 1
}

psql_file() { docker exec -i "$PGC" psql -U myfinances -d myfinances -q "$@"; }

# ------------------------------------------------------------------------------------ --fast
setup_fast() {
  echo "== credential and database (host JVM mode)"
  PW=$(rand)
  local hash_line
  hash_line=$(cd backend && AUTH_PASSWORD=$PW GRADLE_BUILD_DIR=$SCRATCH/new/build \
    ./gradlew -I gradle/relocate-build-dir.init.gradle -q generateAuthHash 2>/dev/null)
  HASH=${hash_line#AUTH_PASSWORD_HASH=\'}
  HASH=${HASH%\'}
  local pg_image pg_port
  pg_image=$(sed -n 's/^[[:space:]]*image:[[:space:]]*\(postgres:[^[:space:]]*\).*/\1/p' docker-compose.yml | head -1)
  PG_PORT=$(free_port)
  CONTAINER=mf-bench-pg-$$
  # --tmpfs: the postgres image declares its data directory a volume, which would outlive the container.
  docker run -d --name "$CONTAINER" --tmpfs /var/lib/postgresql -p "127.0.0.1:$PG_PORT:5432" \
    -e POSTGRES_USER=myfinances -e POSTGRES_PASSWORD="$PW" -e POSTGRES_DB=myfinances "$pg_image" >/dev/null
  PGC=$CONTAINER
  until docker exec "$CONTAINER" pg_isready -U myfinances >/dev/null 2>&1; do sleep 1; done

  echo "== building and starting the backend under test ($(git rev-parse --short HEAD))"
  (cd backend && GRADLE_BUILD_DIR=$SCRATCH/new/build ./gradlew -I gradle/relocate-build-dir.init.gradle -q bootJar)
  local api_port
  api_port=$(free_port)
  fast_backend "$(ls "$SCRATCH"/new/build/libs/*.jar | grep -v plain | head -1)" "$api_port" new
  export BENCH_API=http://127.0.0.1:$api_port

  if [ -n "$BASELINE" ]; then
    echo "== building and starting the baseline backend ($BASELINE)"
    WORKTREE=$SCRATCH/baseline-src
    git worktree add --detach "$WORKTREE" "$BASELINE" >/dev/null
    (cd "$WORKTREE/backend" && ./gradlew -q bootJar)
    local base_port
    base_port=$(free_port)
    fast_backend "$(ls "$WORKTREE"/backend/build/libs/*.jar | grep -v plain | head -1)" "$base_port" baseline
    export BENCH_BASELINE_API=http://127.0.0.1:$base_port
  fi

  if [ "$TTFC" = 1 ]; then
    echo "== building the frontend"
    (cd frontend && npx vite build --outDir "$SCRATCH/dist" --emptyOutDir >/dev/null)
    local front_port
    front_port=$(free_port)
    node "$BENCH/proxy.mjs" "$SCRATCH/dist" "$front_port" "${BENCH_API##*:}" &
    PIDS+=($!)
    export BENCH_FRONTEND=http://127.0.0.1:$front_port
  fi
}

fast_backend() { # jar, port, label
  SPRING_PROFILES_ACTIVE=prod DB_HOST=localhost DB_PORT=$PG_PORT DB_NAME=myfinances DB_USER=myfinances \
    DB_PASSWORD=$PW SERVER_PORT=$2 SERVER_ADDRESS=127.0.0.1 LOGGING_FILE_NAME=$SCRATCH/$3.log \
    AUTH_USERNAME=bench AUTH_PASSWORD_HASH=$HASH \
    java -jar "$1" >"$SCRATCH/$3.out" 2>&1 &
  PIDS+=($!)
  wait_for_http "http://127.0.0.1:$2/api/auth/session" 180 "the $3 backend" "file:$SCRATCH/$3.out"
}

# --------------------------------------------------------------------------------- Compose mode
setup_compose() {
  PROJECT=mf-bench-$$
  local tag build=1 svc
  if [ -n "$IMAGE_TAG_OPT" ]; then tag=$IMAGE_TAG_OPT; build=0; else tag=bench-$$; fi
  echo "== prod-shaped stack: project $PROJECT, image tag $tag"
  STACK=$SCRATCH/stack
  mkdir -p "$STACK/backups"
  local native=$STACK
  if command -v cygpath >/dev/null 2>&1; then native=$(cygpath -m "$STACK"); fi # C:/... for bind mounts
  cp docker-compose.prod.yml "$BENCH/bench.override.yml" "$STACK/"
  COMPOSE_FILES=(-f docker-compose.prod.yml -f bench.override.yml)
  if [ -n "$CPUS" ]; then
    printf 'services:\n  postgres:\n    cpus: %s\n  backend:\n    cpus: %s\n  frontend:\n    cpus: %s\n' \
      "$CPUS" "$CPUS" "$CPUS" >"$STACK/cpus.override.yml"
    COMPOSE_FILES+=(-f cpus.override.yml)
  fi

  local image_prefix=ghcr.io/hmcaio/my-finances
  if [ "$build" = 1 ]; then
    echo "== building the images from this checkout ($(git rev-parse --short HEAD))"
    local pids=() failed=0
    for svc in backend frontend backup; do
      docker build -q -t "$image_prefix-$svc:$tag" "$svc" >/dev/null &
      pids+=($!)
      BUILT_IMAGES+=("$image_prefix-$svc:$tag")
    done
    for pid in "${pids[@]}"; do wait "$pid" || failed=1; done
    [ "$failed" = 0 ] || { echo "bench: an image build failed" >&2; exit 1; }
  else
    for svc in backend frontend backup; do docker pull -q "$image_prefix-$svc:$tag" >/dev/null; done
  fi

  PW=$(rand)
  local postgres_password hash_line
  postgres_password=$(rand)
  # The README's "Login" recipe, from the backend image under test.
  hash_line=$(AUTH_PASSWORD=$PW MSYS_NO_PATHCONV=1 docker run --rm -e AUTH_PASSWORD --entrypoint java \
    "$image_prefix-backend:$tag" -Dloader.main=com.chm.myfinances.infrastructure.auth.AuthHashGenerator \
    -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher 2>"$SCRATCH/hash.err")
  case $hash_line in
    "AUTH_PASSWORD_HASH='\$argon2id\$"*) ;;
    *) cat "$SCRATCH/hash.err" >&2; echo "bench: the backend image printed no AUTH_PASSWORD_HASH line" >&2; exit 1 ;;
  esac
  FRONTEND_PORT=$(free_port)
  local overrides='IMAGE_TAG|AUTH_USERNAME|AUTH_PASSWORD_HASH|POSTGRES_PASSWORD|FRONTEND_PORT|BIND_ADDR|BACKUP_DIR|BACKUP_AGE_RECIPIENT'
  {
    grep -vE "^($overrides)=" .env.example
    echo "IMAGE_TAG=$tag"
    echo "AUTH_USERNAME=bench"
    echo "$hash_line"
    echo "POSTGRES_PASSWORD=$postgres_password"
    echo "FRONTEND_PORT=$FRONTEND_PORT"
    echo "BIND_ADDR=127.0.0.1"
    echo "BACKUP_DIR=$native/backups"
    echo "BACKUP_AGE_RECIPIENT="
  } >"$STACK/.env"
  unset hash_line

  echo "== guard: is the stack isolated from a real prod stack?"
  dc config --format json >"$SCRATCH/config.json"
  node "$BENCH/guard.mjs" "$SCRATCH/config.json" "$PROJECT" "$tag" "$FRONTEND_PORT" "$native"
  STACK_CREATED=1 # from here on, cleanup may `down -v` this project (and only this one)

  local keygen recipient
  keygen=$(dc run --rm --no-deps -T backup backup-keygen 2>"$SCRATCH/keygen.err") \
    || { cat "$SCRATCH/keygen.err" >&2; echo "bench: backup-keygen failed" >&2; exit 1; }
  recipient=$(grep -o 'age1[0-9a-z]*' <<<"$keygen" | head -n1)
  unset keygen
  [ -n "$recipient" ] || { echo "bench: backup-keygen printed no age recipient" >&2; exit 1; }
  sed "s/^BACKUP_AGE_RECIPIENT=.*/BACKUP_AGE_RECIPIENT=$recipient/" "$STACK/.env" >"$STACK/.env.new" \
    && mv "$STACK/.env.new" "$STACK/.env"

  echo "== starting the stack"
  if ! dc up -d --wait --wait-timeout 300 >"$SCRATCH/up.log" 2>&1; then
    tail -n 15 "$SCRATCH/up.log" >&2
    KEEP=1
    echo "bench: the stack did not become healthy within 300 s (logs kept in $SCRATCH)" >&2
    exit 1
  fi
  PGC=$(dc ps -q postgres)
  export BENCH_API=http://127.0.0.1:$FRONTEND_PORT BENCH_FRONTEND=http://127.0.0.1:$FRONTEND_PORT

  if [ -n "$BASELINE" ]; then
    echo "== building and starting the baseline backend ($BASELINE)"
    WORKTREE=$SCRATCH/baseline-src
    git worktree add --detach "$WORKTREE" "$BASELINE" >/dev/null
    local base_image=$image_prefix-backend:$tag-baseline
    docker build -q -t "$base_image" "$WORKTREE/backend" >/dev/null
    BUILT_IMAGES+=("$base_image")
    # The baseline gets the backend service's own environment and joins the stack's network.
    # `compose config` prints a literal $ as $$ (so its output can be fed back to Compose); an
    # env file takes values as they are, and the Argon2 hash is full of them.
    node -e "const e=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')).services.backend.environment;
      console.log(Object.entries(e).map(([k,v])=>k+'='+String(v).replace(/\\$\\$/g,'\$')).join('\n'))" "$SCRATCH/config.json" >"$SCRATCH/baseline.env"
    local base_port
    base_port=$(free_port)
    BASE_CONTAINER=mf-bench-base-$$
    docker run -d --name "$BASE_CONTAINER" --network "${PROJECT}_default" \
      -p "127.0.0.1:$base_port:8080" --env-file "$SCRATCH/baseline.env" "$base_image" >/dev/null
    wait_for_http "http://127.0.0.1:$base_port/api/auth/session" 180 "the baseline backend" "container:$BASE_CONTAINER"
    export BENCH_BASELINE_API=http://127.0.0.1:$base_port
  fi
}

# ------------------------------------------------------------------------------------------ run
COMPOSE_FILES=()
if [ "$FAST" = 1 ]; then setup_fast; else setup_compose; fi
export BENCH_PASSWORD=$PW BENCH_PG_CONTAINER=$PGC
psql_file <"$BENCH/seed-base.sql"

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
