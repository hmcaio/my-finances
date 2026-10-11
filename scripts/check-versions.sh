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

# Postgres: dev + prod compose, the Testcontainers tag used by every backend test, and the F018
# backup sidecar image (its own pg_dump/pg_restore must match the server major, and the tool-
# version-pinning rule applies to it exactly as to the other three).
pg=$(values 'postgres:[0-9][0-9.]*-alpine' \
  docker-compose.yml docker-compose.prod.yml \
  backend/src/test/java/com/chm/myfinances/TestcontainersConfiguration.java \
  backup/Dockerfile)
expect_one "postgres" $pg

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

exit $status
