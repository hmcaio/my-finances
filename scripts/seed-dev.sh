#!/usr/bin/env bash
# Optional: load sample data (scripts/seed/dev-seed.sql) into the dev stack's Postgres.
#
#   scripts/seed-dev.sh
#
# Needs the dev Postgres up (`docker compose up -d`, or `--profile full`) and the backend started
# at least once, so Flyway has created the schema. Refuses to run when accounts, transactions or
# transfers already hold data. A second dev stack (STACK_NAME, docs/conventions/tooling.md) is
# seeded by exporting the same STACK_NAME. This is dev tooling only: never point it at prod.
set -eu
cd "$(dirname "$0")/.."

if ! docker compose exec -T postgres pg_isready -U myfinances -d myfinances >/dev/null 2>&1; then
  echo "seed-dev: the dev Postgres is not running (docker compose up -d)" >&2
  exit 1
fi

if [ "$(docker compose exec -T postgres psql -U myfinances -d myfinances -Atc \
      "select to_regclass('public.transfers') is not null")" != "t" ]; then
  echo "seed-dev: schema missing; start the backend once so Flyway migrates, then re-run" >&2
  exit 1
fi

docker compose exec -T postgres psql -U myfinances -d myfinances -v ON_ERROR_STOP=1 -q < scripts/seed/dev-seed.sql
echo "seed-dev: sample data loaded"
