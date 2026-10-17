# Backup sidecar

A small standalone image — `postgres:17.11-alpine` + `age` + `rclone` + shell scripts — that takes automated, encrypted, retained Postgres backups of the production stack. Built and published alongside the backend/frontend images; runs as the `backup` service in `docker-compose.prod.yml`, never in dev. Full design: [ADR 0015](../docs/adr/0015-automated-encrypted-backups-sidecar.md) and [docs/features/F018-backups/spec.md](../docs/features/F018-backups/spec.md). Setup, restore and the off-site-copy options are documented in the repo root [README.md](../README.md#backups) — read that first if you're operating the stack; this file is about the sidecar's own code.

## What runs, and when

`entrypoint.sh` runs on container start: reads the `status.json` marker, decides whether a backup is forced (image/build changed since the last recorded one — tagged `pre-upgrade`), stale (older than `BACKUP_MAX_AGE_HOURS`, default 20h) or still fresh, runs `backup.sh` if needed, marks the healthcheck ready, then loops checking hourly for as long as the container stays up. Catch-up rather than cron, matching the app's own on-demand runtime (ADR 0003) — it must back up promptly after the stack has been off for a while, not wait for a fixed clock tick.

`backup.sh` does one run: `pg_dump -Fc` → `age`-encrypt → atomic rename to `myfinances-<UTC timestamp>[.pre-upgrade].dump.age` → optional `rclone` push → prune → update `status.json`. Non-zero exit on any failure, previous backups untouched.

`prune.sh` is a pure function over a list of filenames and "now" — GFS retention (7 daily / 5 weekly / 12 monthly) by calendar bucket, `pre-upgrade` files exempt until a later ordinary backup succeeds. Kept pure specifically so it's unit-testable without a real filesystem or Postgres.

`restore.sh <file> --yes` and `backup-keygen` are one-off manual scripts, run via `docker compose run --rm --no-deps backup ...` — see the root README's Backups section for the exact invocation.

## Testing

```
./test/decide.bats   # forced/stale/fresh decision logic
./test/prune.bats     # GFS bucket selection, boundary cases, pre-upgrade exemption
./test/e2e.sh         # builds the real image, dumps/encrypts/restores against throwaway Postgres containers
```

All three run as part of `scripts/verify.sh backup` from the repo root (what CI runs); `decide.bats`/`prune.bats` need `bats` (bats-core) on `PATH`, `e2e.sh` needs Docker. [docs/conventions/tooling.md](../docs/conventions/tooling.md) has the local one-off-container option for running bats without installing it.

## Logging and secrets

Status (`status.json`) and logs carry ids, counts and timestamps only — never amounts, descriptions or names (ADR 0011), consistent with every other part of this app. The `age` private key is never written to disk by `backup-keygen` and never belongs in `.env` or on this host; losing it makes every existing backup permanently unrecoverable, by design (ADR 0015).
