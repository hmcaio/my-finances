# Backup sidecar

A small standalone image — plain `alpine` (full patch tag) + the Postgres client tools (`postgresql18-client`, major set by `ARG POSTGRES_MAJOR`) + `age` + `rclone` + shell scripts — that takes automated, encrypted, retained Postgres backups of the production stack. Built and published alongside the backend/frontend images; runs as the `backup` service in `docker-compose.prod.yml`, never in dev. Full design: [ADR 0015](../docs/adr/0015-automated-encrypted-backups-sidecar.md) and [docs/features/F018-backups/spec.md](../docs/features/F018-backups/spec.md). Setup, restore and the off-site-copy options are documented in [docs/operations.md](../docs/operations.md#backups) — read that first if you're operating the stack; this file is about the sidecar's own code.

## The image

`backup/Dockerfile` is `FROM alpine:<x.y.z>` plus `apk add postgresql${POSTGRES_MAJOR}-client bash coreutils findutils jq age rclone gosu`; it is deliberately not built on the `postgres` server image (#204, [ADR 0015](../docs/adr/0015-automated-encrypted-backups-sidecar.md), "Amended"): that image declares `VOLUME /var/lib/postgresql/data`, so every sidecar container got an anonymous volume nothing used, and it carries a whole server the sidecar never runs. This image declares no volume. Two pins, both checked by `scripts/check-versions.sh`:

- `POSTGRES_MAJOR` must equal the major of the `postgres` tag in `docker-compose.prod.yml` (`pg_dump`/`pg_restore` must match the server's major). Alpine installs the latest minor of that major, which dumps and restores against any server of the same major; a Postgres major bump changes this ARG in the same PR as the compose files.
- The Alpine tag is a full patch tag, the same release the other images' `-alpine` bases are on (bumping it is a Dependabot PR on `/backup`).

## What runs, and when

`entrypoint.sh` runs on container start: reads the `status.json` marker, decides whether a backup is forced (image/build changed since the last recorded one — tagged `pre-upgrade`), stale (older than `BACKUP_MAX_AGE_HOURS`, default 20h) or still fresh, runs `backup.sh` if needed, marks the healthcheck ready, then loops checking hourly for as long as the container stays up. Catch-up rather than cron, matching the app's own on-demand runtime (ADR 0003) — it must back up promptly after the stack has been off for a while, not wait for a fixed clock tick.

`backup.sh` does one run: `pg_dump -Fc` → `age`-encrypt → atomic rename to `myfinances-<UTC timestamp>[.pre-upgrade].dump.age` → optional `rclone` push → prune → update `status.json`. Non-zero exit on any failure, previous backups untouched.

`prune.sh` is a pure function over a list of filenames and "now" — GFS retention (7 daily / 5 weekly / 12 monthly) by calendar bucket, `pre-upgrade` files exempt until a later ordinary backup succeeds. Kept pure specifically so it's unit-testable without a real filesystem or Postgres.

`restore.sh <file> --yes` and `backup-keygen` are one-off manual scripts, run via `docker compose run --rm --no-deps backup ...` — see the Backups section of [docs/operations.md](../docs/operations.md#backups) for the exact invocation (and its setup order: `.env` auth variables must be set and the backups volume created, or `BACKUP_DIR` set, before `backup-keygen`).

An on-demand backup is `docker compose -f docker-compose.prod.yml exec -u <BACKUP_UID>:<BACKUP_GID> backup backup.sh` (1000:1000 by default). `entrypoint.sh` only runs `backup.sh` when `decide.sh` says forced or stale, so restarting the container never forces one, and `exec` without `-u` runs as root: `backup.sh` then collides with the `/tmp` scratch files (`pg_dump.err`) the unprivileged sidecar left behind and records `dump_failed`.

## Testing

```
./test/decide.bats   # forced/stale/fresh decision logic
./test/prune.bats     # GFS bucket selection, boundary cases, pre-upgrade exemption
./test/e2e.sh         # builds the real image, dumps/encrypts/restores against throwaway Postgres containers
```

All three run as part of `scripts/verify.sh backup` from the repo root (what CI runs); only Docker is needed: `decide.bats`/`prune.bats` run in an image built from `backup/test/Dockerfile` (the sidecar's Alpine plus bats), so there is no bats-core to install, and `e2e.sh` builds the real sidecar image. [docs/conventions/tooling.md](../docs/conventions/tooling.md) has the details.

## Logging and secrets

Status (`status.json`) and logs carry ids, counts and timestamps only — never amounts, descriptions or names (ADR 0011), consistent with every other part of this app. The `age` private key is never written to disk by `backup-keygen` and never belongs in `.env` or on this host; losing it makes every existing backup permanently unrecoverable, by design (ADR 0015).
