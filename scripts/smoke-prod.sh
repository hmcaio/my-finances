#!/usr/bin/env bash
# Production smoke test (ADR 0032, docs/conventions/tooling.md): starts docker-compose.prod.yml,
# unchanged, against locally built images behind a throwaway login and asserts what only the real
# images show: every service healthy, ports bound to loopback, the login, nginx's security-header
# and cache matrix, the sidecar's backup status and a clean backend log. Run it through
# `bash scripts/verify.sh smoke`, which builds the images; CI calls it on pull requests and in the
# tag run before anything is pushed.
#
# Images under test come from SMOKE_BACKEND_IMAGE, SMOKE_FRONTEND_IMAGE and SMOKE_BACKUP_IMAGE
# (default my-finances-<name>:smoke). They are tagged ghcr.io/hmcaio/my-finances-<name>:smoke and
# the stack runs with IMAGE_TAG=smoke, so the compose file needs no edit and nothing is pulled.
#
# Isolation. The prod compose file hardcodes container_name (my-finances-*-prod) and the
# my-finances-logs-prod volume, so it cannot run beside a real prod stack: the script refuses to
# start when any of them exists. Everything else is throwaway: its own compose project
# (my-finances-smoke), a temp directory holding a copy of the compose file, the .env, the backups
# bind folder and the cookie jar, a random login password and Postgres password, and an age
# recipient made by the documented backup-keygen (the private key is dropped unread). The EXIT trap
# removes the project (`down -v`), the temp directory and the tags this run created. Secrets are
# never echoed.
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1
ROOT=$PWD

PROJECT=my-finances-smoke
TAG=smoke
USER_NAME=smoke
IMAGE_PREFIX=ghcr.io/hmcaio/my-finances
SERVICES=(postgres backup backend frontend)

BACKEND_SRC=${SMOKE_BACKEND_IMAGE:-my-finances-backend:smoke}
FRONTEND_SRC=${SMOKE_FRONTEND_IMAGE:-my-finances-frontend:smoke}
BACKUP_SRC=${SMOKE_BACKUP_IMAGE:-my-finances-backup:smoke}

die() { echo "smoke: $*" >&2; exit 1; }

# --- Precheck: never touch a stack that is not ours -------------------------------------------
for src in "$BACKEND_SRC" "$FRONTEND_SRC" "$BACKUP_SRC"; do
  docker image inspect "$src" >/dev/null 2>&1 || die "image '$src' not found (bash scripts/verify.sh smoke builds them)"
done
clash=$( {
  docker ps -a --format '{{.Names}}' | grep -xE 'my-finances-(postgres|backup|backend|frontend)-prod'
  docker ps -a --filter "label=com.docker.compose.project=$PROJECT" --format '{{.Names}}'
  docker volume ls -q | grep -xE "my-finances-logs-prod|${PROJECT}_.*"
} 2>/dev/null | sort -u)
if [ -n "$clash" ]; then
  echo "smoke: refusing to run: these already exist, and docker-compose.prod.yml hardcodes their names" >&2
  echo "(a real prod stack, or leftovers of an aborted smoke run):" >&2
  while IFS= read -r line; do echo "  $line" >&2; done <<< "$clash"
  die "stop and remove them first (docker rm -f / docker volume rm), then re-run"
fi

# --- Scratch space and cleanup ----------------------------------------------------------------
tmp=$(mktemp -d) || die "mktemp failed"
tmp_native=$tmp
if command -v cygpath >/dev/null 2>&1; then tmp_native=$(cygpath -m "$tmp"); fi   # C:/... for bind mounts
backups_native=$tmp_native/backups
tagged=()

dc() { (cd "$tmp" && docker compose -p "$PROJECT" -f docker-compose.prod.yml "$@"); }

cleanup() {
  local rc=$?
  trap - EXIT
  cd "$ROOT" || true
  dc down -v --remove-orphans >/dev/null 2>&1
  # Anything the precheck proved absent and exists now is ours (the logs volume has a fixed name).
  docker volume ls -q | grep -xE "my-finances-logs-prod|${PROJECT}_.*" | xargs -r docker volume rm >/dev/null 2>&1
  # The sidecar chowns the bind folder to uid 1000, so the host user may not be able to empty it.
  MSYS_NO_PATHCONV=1 docker run --rm -v "$backups_native:/b" --entrypoint sh "$BACKUP_SRC" \
    -c 'rm -rf /b/* /b/.[!.]*' >/dev/null 2>&1
  rm -rf "$tmp"
  [ ${#tagged[@]} -gt 0 ] && docker rmi "${tagged[@]}" >/dev/null 2>&1
  exit $rc
}
trap cleanup EXIT
trap 'exit 130' INT TERM

for name in backend frontend backup; do
  src_var=${name^^}_SRC
  ref=$IMAGE_PREFIX-$name:$TAG
  docker image inspect "$ref" >/dev/null 2>&1 || tagged+=("$ref")
  docker tag "${!src_var}" "$ref" || die "cannot tag ${!src_var}"
done

rand() { head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
password=$(rand)
postgres_password=$(rand)
if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::add-mask::$password"; echo "::add-mask::$postgres_password"; fi

cp docker-compose.prod.yml "$tmp/"
mkdir "$tmp/backups"

# The "Login" recipe in docs/operations.md, from the backend image under test: stdout is the whole
# AUTH_PASSWORD_HASH='...' line (single quotes included), stderr holds notes only.
hash_line=$(AUTH_PASSWORD=$password MSYS_NO_PATHCONV=1 docker run --rm -e AUTH_PASSWORD --entrypoint java "$BACKEND_SRC" \
  -Dloader.main=com.chm.myfinances.infrastructure.auth.AuthHashGenerator \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher 2>"$tmp/hash.err")
case $hash_line in
  "AUTH_PASSWORD_HASH='\$argon2id\$"*) ;;
  *) cat "$tmp/hash.err" >&2; die "the backend image's AuthHashGenerator printed no AUTH_PASSWORD_HASH line" ;;
esac

# .env: .env.example (so the shipped example is what runs) minus the keys set here. The free
# frontend port is left to Docker (FRONTEND_PORT=0) and read back with `compose port`.
overrides='IMAGE_TAG|AUTH_USERNAME|AUTH_PASSWORD_HASH|POSTGRES_PASSWORD|FRONTEND_PORT|BIND_ADDR|BACKUP_DIR|BACKUP_AGE_RECIPIENT'
{
  grep -vE "^($overrides)=" .env.example
  echo "IMAGE_TAG=$TAG"
  echo "AUTH_USERNAME=$USER_NAME"
  echo "$hash_line"
  echo "POSTGRES_PASSWORD=$postgres_password"
  echo "FRONTEND_PORT=0"
  echo "BIND_ADDR=127.0.0.1"
  echo "BACKUP_DIR=$backups_native"
  echo "BACKUP_AGE_RECIPIENT="
} > "$tmp/.env"
unset hash_line

# The keygen from docs/operations.md (BACKUP_DIR is already in .env); only the public recipient is kept.
keygen=$(dc run --rm --no-deps -T backup backup-keygen 2>"$tmp/keygen.err") \
  || { cat "$tmp/keygen.err" >&2; die "backup-keygen failed"; }
recipient=$(grep -o 'age1[0-9a-z]*' <<< "$keygen" | head -n1)
unset keygen
[ -n "$recipient" ] || die "backup-keygen printed no age recipient"
sed "s/^BACKUP_AGE_RECIPIENT=.*/BACKUP_AGE_RECIPIENT=$recipient/" "$tmp/.env" > "$tmp/.env.new" && mv "$tmp/.env.new" "$tmp/.env"

# --- Start and wait for healthy ---------------------------------------------------------------
echo "smoke: starting the stack"
if ! dc up -d --wait --wait-timeout 300 >"$tmp/up.log" 2>&1; then
  tail -n 15 "$tmp/up.log" >&2
  dc ps -a >&2
  for s in backend backup postgres frontend; do
    echo "--- last log lines: $s" >&2
    dc logs --no-color --no-log-prefix --tail 15 "$s" >&2
  done
  die "the stack did not become healthy within 300 s"
fi

# --- Assertions -------------------------------------------------------------------------------
cd "$tmp" || die "cd $tmp"   # relative file names from here: no path conversion for curl/awk
checks=0
failures=()
check() { checks=$((checks + 1)); if [ "$2" = "$3" ]; then :; else failures+=("$1: got '$2', want '$3'"); echo "  FAIL $1: got '$2', want '$3'" >&2; fi; }

port=$(dc port frontend 80 | head -n1 | sed 's/.*://' | tr -d '\r')
[ -n "$port" ] || die "no published port for the frontend"
base=http://127.0.0.1:$port
status=

# req <name> <path> [curl args]: response headers in <name>.h, body in <name>.b, code in $status.
req() {
  local name=$1 path=$2; shift 2
  status=$(curl -sS --max-time 30 -o "$name.b" -D "$name.h" -w '%{http_code}' "$@" "$base$path") || status=000
}
hcount() { grep -ci "^$2:" "$1.h"; true; }
hvalue() { grep -i -m1 "^$2:" "$1.h" | sed 's/^[^:]*: *//' | tr -d '\r'; }

# `up --wait` also passes for a service that has no healthcheck (running is enough), so read the
# status back: a healthcheck removed from any service, the frontend's included, fails here.
echo "smoke: health"
for s in "${SERVICES[@]}"; do
  check "$s container health" "$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "my-finances-$s-prod" | tr -d '\r')" healthy
done

echo "smoke: ports"
for s in "${SERVICES[@]}"; do
  bound=$(docker inspect -f '{{range $p, $b := .NetworkSettings.Ports}}{{range $b}}{{.HostIp}}:{{.HostPort}} {{end}}{{end}}' "my-finances-$s-prod" | tr -d '\r')
  if [ "$s" = frontend ]; then
    other=0; n=0
    for b in $bound; do n=$((n + 1)); case $b in 127.0.0.1:[0-9]*) ;; *) other=$((other + 1)) ;; esac; done
    check "frontend publishes one port, on 127.0.0.1 (published: ${bound% })" "$n published, $other elsewhere" "1 published, 0 elsewhere"
  else
    check "$s publishes no port" "${bound% }" ""
  fi
done

# The sidecar image declares no VOLUME (#204; built on the postgres image it got an anonymous
# volume per container at /var/lib/postgresql/data, the volume Postgres 17 declared): its only mounts are the two compose binds.
mounts=$(docker inspect -f '{{range .Mounts}}{{.Type}}:{{.Destination}} {{end}}' my-finances-backup-prod | tr -s ' \r\n' '\n' | grep . | sort | tr '\n' ' ')
check "backup mounts only the backups folder and rclone.conf, no inherited volume" "${mounts% }" "bind:/backups bind:/etc/rclone/rclone.conf"

echo "smoke: edge and login"
for i in $(seq 1 30); do req shell / ; [ "$status" = 200 ] && break; sleep 1; done

# Security headers: each exactly once, on every kind of response nginx produces or proxies.
sec_once() {
  local f=$1 label=$2 h
  for h in Content-Security-Policy X-Frame-Options X-Content-Type-Options Referrer-Policy; do
    check "$label: $h count" "$(hcount "$f" "$h")" 1
  done
  check "$label: Cache-Control count" "$(hcount "$f" Cache-Control)" 1
}

check "GET / status" "$status" 200
sec_once shell "/"
check "/ Cache-Control" "$(hvalue shell Cache-Control)" "no-cache"

req index /index.html
check "GET /index.html status" "$status" 200
sec_once index "/index.html"
check "/index.html Cache-Control" "$(hvalue index Cache-Control)" "no-cache"

# The frontend healthcheck target: answered by nginx itself, same headers as everything else, and
# absent from the access log (a probe every few seconds would bury the request-id lines).
req healthz /healthz
check "GET /healthz status" "$status" 200
check "GET /healthz body" "$(tr -d '\r\n' < healthz.b)" ok
sec_once healthz "/healthz"
check "/healthz Cache-Control" "$(hvalue healthz Cache-Control)" "no-store"

req deep /ledger
check "GET /ledger status" "$status" 200
sec_once deep "/ledger"
check "/ledger Cache-Control" "$(hvalue deep Cache-Control)" "no-cache"

asset=$(grep -o '/assets/[^"]*\.js' index.b | head -n1)
[ -n "$asset" ] || die "no /assets/*.js reference in index.html"
req asset "$asset"
check "GET $asset status" "$status" 200
sec_once asset "/assets/*.js"
check "/assets/*.js Cache-Control" "$(hvalue asset Cache-Control)" "public, max-age=31536000, immutable"

req missing /assets/x.js
check "GET /assets/x.js status" "$status" 404
sec_once missing "missing /assets/x.js"
check "missing /assets/x.js Cache-Control" "$(hvalue missing Cache-Control)" "no-store"

# Swagger UI and the OpenAPI document are off in prod: nginx only has the SPA fallback for them
# (the shell, never JSON), and the backend itself answers 404 (checked below with a session).
for p in /swagger-ui.html /v3/api-docs; do
  req doc "$p"
  check "GET $p serves the SPA shell, not the document" "$(grep -c -e '"openapi"' -e 'swagger-ui-bundle' doc.b; true)" 0
  check "GET $p returns the shell" "$(cmp -s doc.b shell.b && echo yes || echo no)" yes
done

req unauth /api/accounts
check "unauthenticated GET /api/accounts status" "$status" 401

req session /api/auth/session -c jar
check "GET /api/auth/session status (no login)" "$status" 401
sec_once session "/api/auth/session (401)"
xsrf=$(awk -F'\t' '$6 == "XSRF-TOKEN" { t = $7 } END { print t }' jar | tr -d '\r')
check "XSRF-TOKEN cookie issued" "$([ -n "$xsrf" ] && echo yes || echo no)" yes

login_json() { printf '{"username":"%s","password":"%s"}' "$USER_NAME" "$1"; }
post_login() { # <name> <password>
  xsrf=$(awk -F'\t' '$6 == "XSRF-TOKEN" { t = $7 } END { print t }' jar | tr -d '\r')
  req "$1" /api/auth/login -b jar -c jar -X POST -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $xsrf" --data-binary @- <<< "$(login_json "$2")"
}
post_login wrong "wrong-$password"
check "login with a wrong password status" "$status" 401
post_login login "$password"
check "login status" "$status" 200
sec_once login "login POST"

req session2 /api/auth/session -b jar
check "GET /api/auth/session status (logged in)" "$status" 200
req accounts /api/accounts -b jar
check "authenticated GET /api/accounts status" "$status" 200

echo "smoke: backend routes the nginx fallback hides"
sid=$(awk -F'\t' '$6 == "JSESSIONID" { t = $7 } END { print t }' jar | tr -d '\r')
backend_status() { dc exec -T backend wget -S -O /dev/null --header "Cookie: JSESSIONID=$sid" "http://127.0.0.1:8080$1" 2>&1 | grep -o 'HTTP/1\.[01] [0-9]*' | head -n1 | cut -d' ' -f2; }
check "backend GET /api/backup-status with the session (control)" "$(backend_status /api/backup-status)" 200
check "backend GET /v3/api-docs status" "$(backend_status /v3/api-docs)" 404
check "backend GET /swagger-ui.html status" "$(backend_status /swagger-ui.html)" 404

echo "smoke: backup status"
backup_ok() { req backup /api/backup-status -b jar; [ "$status" = 200 ] && grep -q '"state":"OK"' backup.b; }
state_ok=no
for _ in 1 2 3 4 5 6; do backup_ok && { state_ok=yes; break; }; sleep 5; done
if [ $state_ok = no ]; then   # docs/operations.md, "Take a backup now": the first start-time backup may predate readiness
  dc exec -T -u 1000:1000 backup backup.sh >/dev/null 2>&1
  for _ in 1 2 3 4 5 6; do backup_ok && { state_ok=yes; break; }; sleep 5; done
fi
check "/api/backup-status reaches state OK (last: $(grep -o '"state":"[A-Z]*"' backup.b | head -n1), lastError $(grep -o '"lastError":[^,]*' backup.b | head -n1))" "$state_ok" yes

echo "smoke: frontend log"
dc logs --no-color --no-log-prefix frontend > frontend.log 2>&1
check "frontend access-log lines for /healthz (probes and the request above)" "$(grep -c '/healthz' frontend.log; true)" 0

echo "smoke: backend log"
dc logs --no-color --no-log-prefix backend > backend.log 2>&1
check "backend log ERROR lines" "$(grep -c ' ERROR ' backend.log; true)" 0
check "backend log stack-trace lines" "$(grep -cE '^[[:space:]]+at [A-Za-z0-9_.$]+\(|^Caused by:' backend.log; true)" 0

if [ ${#failures[@]} -ne 0 ]; then
  echo >&2
  echo "smoke: ${#failures[@]} of $checks checks failed:" >&2
  printf '  - %s\n' "${failures[@]}" >&2
  exit 1
fi
echo "smoke: all $checks checks passed"
