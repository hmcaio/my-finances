# F018 — Backups

## Summary
The production stack (F014) stores everything in one Docker volume and has no backup. F018 adds automated, encrypted, retained `pg_dump` backups run by a sidecar container, an optional off-site copy, a status the UI can show, and a safe restore script. Infrastructure plus one small read-only backend endpoint and a banner; no domain or data-model change. Decision record: [ADR 0015](../../adr/0015-automated-encrypted-backups-sidecar.md).

It must survive two failures: data destroyed on the same machine (`down -v`, a bad migration, an app bug), and the machine or disk dying. The app runs on demand (PRD §7.3, ADR 0003), so scheduling is catch-up based, not cron-only. It must work on Docker Desktop for Windows, WSL2 and a Linux host or VPS.

## Scope
- A `backup` sidecar image and compose service (prod only), published to GHCR by CI.
- Encrypted `pg_dump -Fc` backups, catch-up scheduling, forced pre-upgrade backup, GFS retention.
- Storage: `BACKUP_DIR` bind mount, else an external named volume; optional `rclone` push.
- A status marker, a backend read endpoint, a frontend banner.
- `restore` and `backup-keygen` scripts.
- ADR 0015, docs, CHANGELOG entry. Out of scope: see [Non-goals](#non-goals).

## Decisions

| Decision | Why |
|---|---|
| `pg_dump -Fc` of the whole DB, including `flyway_schema_history`. | Dataset is tiny; custom format is compressed and restorable per table. WAL/PITR and volume tars were rejected (overkill, resp. need the DB stopped). Including the Flyway history leaves the app's migration state consistent after a restore. |
| Sidecar container, not a backend `@Scheduled` job, not a host scheduler. | Works the same on Windows, WSL2 and Linux with nothing installed on the host; keeps `pg_dump` (a Postgres-version-matched tool) out of the backend image; the backend stays unaware of backups apart from reading status. |
| Catch-up trigger: on sidecar start, back up if the last success is older than `BACKUP_MAX_AGE_HOURS` (default 20); then check hourly while running. | Same logic as ADR 0003: a stack that is off for days must back up as soon as it comes up, and one left running gets about one backup per day. |
| A sidecar start whose **build id** differs from the one in the marker is forced and tagged `pre-upgrade`; the backend waits for that first check. | A migration on upgrade is the likeliest way to lose data. The sidecar image is built in the same CI run as the backend and shares its tag, so its baked-in build id (git SHA) changes exactly when the backend does, including for `IMAGE_TAG=latest`, where a tag comparison would never fire. Reading the backend image digest was rejected: it needs the Docker socket mounted, which is root-equivalent on the host. Comparing Flyway versions doesn't work either: the sidecar runs before the new migrations do. |
| A failed backup never blocks the app: the sidecar's healthcheck turns healthy once the start-time check finishes, whether it succeeded or failed, and the failure shows as `FAILING` in the banner. | Blocking startup would let an unrelated `rclone` outage or a full backup disk stop the app; a visible warning is enough for a single local user. |
| `pre-upgrade` files are exempt from pruning until a later normal backup succeeds. | A migration bug may only show up on the next start; the file that predates it must not be pruned by the very backup taken after the bad migration. |
| GFS retention: 7 daily, 5 weekly, 12 monthly, chosen by calendar bucket (newest file in each day / ISO week / month). | Deeper history than "last N files", and bursts of restarts can't push out the only old backup. Applied identically to the local folder and the `rclone` remote. |
| `age` public-key encryption (`BACKUP_AGE_RECIPIENT` in `.env`); the private key is never on the host. | A stolen `.env` or compromised VPS can't decrypt old backups. Cost: losing the private key loses every backup; docs and the keygen helper say so. A passphrase in `.env` was rejected because it hands the host the decrypt secret. |
| Storage: `BACKUP_DIR` bind mount when set; otherwise the **external** volume `my-finances-backups-prod`. | A synced folder gives an off-machine copy on any OS with no cloud credentials in the repo. `external: true` means compose never removes the volume, so `docker compose down -v` (used in the smoke test) can't erase the backups along with the data. |
| Optional `rclone` push (`BACKUP_RCLONE_REMOTE`, config via a mounted `rclone.conf`), off by default, of the already-encrypted file. | A VPS has no synced folder, so a local bind mount alone doesn't satisfy "survive disk death". A push failure is a backup failure in the status marker. |
| Files are written to a temporary name and atomically renamed; the sidecar runs as `BACKUP_UID`/`BACKUP_GID` (default 1000). | Sync clients and antivirus must never see half-written files; bind-mount ownership differs between Docker Desktop and Linux hosts. |
| Status: a JSON marker (`status.json`) in the backup folder, read by the backend through a read-only mount. Fields: `lastSuccessAt`, `lastAttemptAt`, `lastError` (category only), `imageTag`, `buildId`, `schemaVersion`, `targetType` (`bind`/`volume`), `remoteConfigured`, `remoteOk`, `count`. | No new network surface, no schema, no Docker socket. A missing folder means `UNKNOWN`, not a failure. No amounts, descriptions or names anywhere (ADR 0011). |
| Restore always writes a `pre-restore-<timestamp>` safety dump of the current DB first and requires `--yes`. | Restore is destructive; the safety dump makes a wrong file or wrong key recoverable. |
| The backup trigger is a plain script. | A later "back up now" endpoint or button can call it without redesign. |

## Sidecar

### Image (`backup/Dockerfile`)
`postgres:17-alpine` (`pg_dump`/`pg_restore` must match the server major) + `age` + `rclone` + `bash`, `coreutils` and `jq`. Scripts under `backup/scripts/`. Built with `ARG BUILD_ID` (the git SHA), written to `/etc/backup-build-id`. CI builds and pushes `ghcr.io/hmcaio/my-finances-backup:<tag>` next to the other two images.

### Scripts
- `backup.sh` — one run: `pg_dump -Fc` → `age -r "$BACKUP_AGE_RECIPIENT"` → temp name → atomic rename to `myfinances-<UTC yyyymmddThhmmssZ>[.pre-upgrade].dump.age` → optional `rclone copy` → prune → update `status.json`. Exits non-zero on any failure, leaving previous backups untouched. Refuses to run without `BACKUP_AGE_RECIPIENT`.
- `entrypoint.sh` — on start: read the marker, decide forced (`pre-upgrade`), stale or fresh; run `backup.sh` if needed; then mark the healthcheck ready; then loop hourly running the staleness check.
- `prune.sh` — pure function over a list of file names and "now": prints the files to delete. All GFS and `pre-upgrade` logic lives here so it is testable.
- `restore.sh <file> --yes` — see [Restore](#restore).
- `backup-keygen` — runs `age-keygen`, prints the recipient (goes in `.env`) and the private key, with a warning to store the private key off this machine now.

### Naming and selection
File names encode the UTC timestamp, so pruning never depends on mtime (which sync clients rewrite). Buckets: day = UTC date, week = ISO week, month = calendar month. Keep the newest file in each of the last 7 days, last 5 ISO weeks and last 12 months; a file kept by any bucket is kept. `pre-upgrade` files are kept regardless until a later non-`pre-upgrade` file exists. Only files matching the naming pattern are ever considered for deletion.

### Compose (`docker-compose.prod.yml`)
- New service `backup`: `image: ghcr.io/hmcaio/my-finances-backup:${IMAGE_TAG}`, `restart: unless-stopped`, `depends_on: postgres: service_healthy`, the shared `x-logging` anchor, `user: "${BACKUP_UID:-1000}:${BACKUP_GID:-1000}"`.
- Mounts: `${BACKUP_DIR:-my-finances-backups-prod}:/backups` (a value with a path separator is a bind mount, otherwise the volume name), plus an optional read-only `rclone.conf`.
- Healthcheck: healthy once the start-time backup check has finished (success or a recorded failure), so a backup that *fails* still lets the app start; the failure is surfaced as a warning, not an outage.
- `backend` gains `depends_on: backup: condition: service_healthy` and a read-only `/backups` mount.
- Volume `my-finances-backups-prod` declared `external: true` with an explicit `name:`; the README and CHANGELOG `Upgrade:` line give the one-time `docker volume create my-finances-backups-prod`.
- New `.env.example` entries: `BACKUP_AGE_RECIPIENT`, `BACKUP_DIR`, `BACKUP_UID`, `BACKUP_GID`, `BACKUP_MAX_AGE_HOURS`, `BACKUP_RCLONE_REMOTE`.
- Dev `docker-compose.yml` is untouched (ADR 0006).

### Restore
`docker compose -f docker-compose.prod.yml run --rm --no-deps backup restore.sh <file> --yes`, with the private key supplied at run time (a key file mounted for the one-off run, never stored in `.env`). Steps: stop the `backend` and `frontend` containers, take the `pre-restore-<ts>` safety dump, drop and recreate the database, `pg_restore`, restart the two containers. Fails before touching anything if the file can't be decrypted or `pg_restore --list` can't read it. Documented in the README, including restoring onto a new machine.

## Backend
- `GET /api/backup-status` returns the marker fields plus a derived `state`: `OK`, `STALE` (last success older than 48h), `FAILING` (last attempt failed), `UNKNOWN` (marker missing or unreadable), and `localOnly` (no bind mount and no remote). Reads `BACKUP_STATUS_PATH` (default `/backups/status.json`); never throws on a missing or malformed file.
- Hexagonal placement per backend `CLAUDE.md`: a small read port in `application/` and a file-reading adapter in `infrastructure/`; the staleness rule is a pure function tested first. No new table.
- The endpoint returns ids, counts and timestamps only; the marker never contains amounts, descriptions or names.
- Dev (no marker): `UNKNOWN`, which the frontend renders as nothing.

## Frontend
- A `BackupStatusBanner` in the app shell: warning when `STALE` or `FAILING` (states the last success time), an info banner for `localOnly`, nothing for `OK`/`UNKNOWN`. Dismissal is per-session only; it reappears next load while the condition holds.
- Uses the generated API types (`npm run generate-api-types`) and the existing API-client conventions; no `conflictMessage` needed (read-only GET).

## Testing
- **Pruning (bats, table-driven)**: daily/weekly/monthly bucket selection, boundary cases (ISO week across a year, month lengths), gaps from an on-demand stack, `pre-upgrade` exemption and its release, idempotence, and that unknown files in the folder are never deleted.
- **Decision logic (bats)**: forced vs stale vs fresh from a marker and a build id; a missing or corrupt marker means back up now.
- **End-to-end**: dump a seeded Postgres, encrypt with a throwaway `age` key pair, restore into a fresh Postgres, compare row counts and `flyway_schema_history`. Also covers a wrong key (fails cleanly, nothing changed) and the safety dump.
- **Backend**: status adapter (missing, malformed, valid marker) and state derivation, in the existing test tiers (ADR 0013). **Frontend**: banner states with MSW.
- **Host matrix (manual, in the plan)**: Docker Desktop on Windows, WSL2 Ubuntu, a plain Linux Docker host. CI runs the bats and end-to-end tests on Linux.

## Docs
- ADR 0015 and its README row; `docs/features/README.md` row.
- Root `CLAUDE.md`: the backups volume is external and survives `down -v`; the prod stack now has a fourth service and image. Backend `CLAUDE.md`: the read-only status port.
- README: backup setup (keygen, `.env`, `docker volume create`, `BACKUP_DIR`, rclone), restore, and a notice that the app has no authentication and a VPS needs private access.
- `CHANGELOG.md` `[Unreleased]` entry with an `Upgrade:` sub-line (new service and image, new env vars, the one-time volume creation).

## Non-goals
- Point-in-time recovery / WAL archiving.
- Backups of the dev stack.
- UI-triggered "back up now" or restore buttons.
- Backing up the logs volume, `.env` or the rclone config.
- Automated periodic restore verification on the host (the sidecar has no decrypt key by design; restore is verified in CI with a throwaway key).
- VPS hardening, authentication or exposure controls — a future feature will widen the system's scope beyond local use; F018 only documents the assumption.
- Unrelated to F013 (user-facing data export), which is not disaster recovery.

## Dependencies
F014 (`docker-compose.prod.yml`, GHCR publishing in `ci.yml`, `.env.example`), F016 (`x-logging` anchor and the ids-and-counts-only logging rule, ADR 0011). Independent of F003–F013 in behaviour.
