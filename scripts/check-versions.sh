#!/usr/bin/env bash
# Fails when the same tool is pinned to different versions in different files (issue #73), so dev,
# CI and prod can't drift apart silently. Run from anywhere; CI runs it on every push.
# Dependabot bumps image tags one file at a time, so a bump that misses a sibling fails here.
set -u
cd "$(dirname "$0")/.."
status=0

fail() { echo "MISMATCH: $1" >&2; status=1; }

# Distinct values of a regex across files (one value per line), or fail if a file has none.
values() {
  local pattern=$1; shift
  for f in "$@"; do
    grep -ohE "$pattern" "$f" || fail "$f has no match for $pattern"
  done | sort -u
}

expect_one() {
  local label=$1; shift
  local count
  count=$(printf '%s\n' "$@" | grep -c .)
  if [ "$count" -ne 1 ]; then
    fail "$label is pinned to $count different versions:"
    printf '  %s\n' "$@" >&2
  fi
}

# Postgres: dev + prod compose and the Testcontainers tag used by every backend test.
pg=$(values 'postgres:[0-9][0-9.]*-alpine' \
  docker-compose.yml docker-compose.prod.yml \
  backend/src/test/java/com/chm/myfinances/TestcontainersConfiguration.java)
expect_one "postgres" $pg

# F018 backup sidecar (backup/Dockerfile, #204): built on plain Alpine, not on the postgres image,
# so its pg_dump/pg_restore are pinned by major (`ARG POSTGRES_MAJOR`, installed as
# `postgresql${POSTGRES_MAJOR}-client`), and that major must be the one of the postgres tag above
# (Alpine ships the client at the latest minor of the major, which works against any server of it).
# Its Alpine base is pinned by a full patch tag like every other image.
backup_major=$(grep -E '^ARG POSTGRES_MAJOR=[0-9]+$' backup/Dockerfile | sed -E 's/^ARG POSTGRES_MAJOR=//')
if [ -z "$backup_major" ]; then
  fail "backup/Dockerfile has no 'ARG POSTGRES_MAJOR=<major>' line"
else
  for tag in $pg; do
    server_major=$(printf '%s' "$tag" | sed -E 's/^postgres:([0-9]+).*/\1/')
    [ "$server_major" = "$backup_major" ] \
      || fail "backup/Dockerfile POSTGRES_MAJOR=$backup_major does not match the server major of $tag (compose files, Testcontainers)"
  done
fi
grep -qF 'postgresql${POSTGRES_MAJOR}-client' backup/Dockerfile \
  || fail 'backup/Dockerfile must install "postgresql${POSTGRES_MAJOR}-client", so the pinned major is the installed one'
grep -qE '^FROM alpine:[0-9]+\.[0-9]+\.[0-9]+$' backup/Dockerfile \
  || fail "backup/Dockerfile must be 'FROM alpine:<x.y.z>' (a full patch tag, never a floating one)"

# Node: frontend/.nvmrc is the source of truth for CI (setup-node reads it) and native dev
# (engines); the Dockerfile and the dev compose service must use the same version.
nvmrc=$(tr -d '[:space:]' < frontend/.nvmrc)
for f in frontend/Dockerfile docker-compose.yml; do
  grep -qE "node:${nvmrc//./\.}-alpine" "$f" || fail "$f does not use node:${nvmrc}-alpine (frontend/.nvmrc)"
done

# Java: JDK and JRE images must be the same release, in the Dockerfile and the dev compose service.
java=$(values 'eclipse-temurin:[0-9][0-9A-Za-z._]*-(jdk|jre)' backend/Dockerfile docker-compose.yml \
  | sed -E 's/-(jdk|jre)$//' | sort -u)
expect_one "eclipse-temurin" $java

# Trivy (image vulnerability scan): scripts/trivy/Dockerfile is the only place the scanner is named
# (verify.sh reads the reference from it), so there are no siblings to compare; this checks the pin
# itself is a full patch tag plus a digest, the form that survived the March 2026 tag hijack.
grep -qE '^FROM aquasec/trivy:[0-9]+\.[0-9]+\.[0-9]+@sha256:[0-9a-f]{64}$' scripts/trivy/Dockerfile \
  || fail "scripts/trivy/Dockerfile must be 'FROM aquasec/trivy:<x.y.z>@sha256:<digest>' (tag and digest)"

exit $status
