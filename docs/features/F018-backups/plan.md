# F018 — Action Plan

**Depends on**: F014, F016.

Suggested order: pure logic first (test-first), then the sidecar, compose and CI, then the backend and frontend status, then docs. One feature branch (`feature/f018-backups`), one commit per phase. Host testing needs Docker running.

## Phase 0 — Decision record
- [x] `docs/adr/0015-automated-encrypted-backups-sidecar.md` and its row in `docs/adr/README.md`.
- [x] `docs/features/F018-backups/{spec,plan}.md` and the `docs/features/README.md` row.

## Phase 1 — Backup logic (test first)
- [x] Add `bats-core` as the test runner for `backup/` with a `backup/test/` folder; document `bats backup/test` in the README/`CLAUDE.md`.
- [x] Test first: `prune.bats` — table-driven cases from the spec (day/ISO-week/month buckets, year boundary, month lengths, on-demand gaps, `pre-upgrade` exemption and release, idempotence, foreign files untouched).
- [x] Implement `backup/scripts/prune.sh` (file names + "now" in, files to delete out; no side effects).
- [x] Test first: `decide.bats` — forced (`pre-upgrade`) vs stale vs fresh from a marker and a build id; a missing or corrupt marker means back up now.
- [x] Implement the decision function used by `entrypoint.sh`.

## Phase 2 — Sidecar image and scripts
- [x] `backup/Dockerfile` (`postgres:17-alpine` + `age` + `rclone` + `bash`/`jq`, `ARG BUILD_ID` written to `/etc/backup-build-id`).
- [x] `backup.sh`: dump → `age` → temp file → atomic rename → optional `rclone copy` → prune → write `status.json`. Non-zero exit on any failure with previous backups untouched; refuses to run without `BACKUP_AGE_RECIPIENT`; logs file names and counts only, never row data.
- [x] `entrypoint.sh`: start-time decision, healthcheck flag, hourly loop.
- [x] `restore.sh`: pre-flight (decrypt + `pg_restore --list`) before touching anything, `pre-restore-<ts>` safety dump, recreate DB, `pg_restore`; requires `--yes`. Stopping/restarting the `backend`/`frontend` containers is a host-level `docker compose` step around invoking this script (see README) — the backup container is deliberately never given the Docker socket, the same reasoning ADR 0015 already gives against reading the backend image's digest.
- [x] `backup-keygen` helper with the loud "store the private key now" output.
- [x] End-to-end test (`backup/test/e2e.sh`, scripted `docker run` job): seed Postgres, backup, restore into a fresh Postgres, compare row counts and `flyway_schema_history`; a wrong key fails cleanly; the safety dump exists. Verified locally against real Docker (not just CI).

## Phase 3 — Compose, env and CI
- [x] `docker-compose.prod.yml`: `backup` service (image, user, mounts, healthcheck, `x-logging`, `depends_on: postgres`), `backend` `depends_on: backup: service_healthy` and read-only `/backups` mount, external volume `my-finances-backups-prod` with an explicit `name:`. Verified with `docker compose -f docker-compose.prod.yml config` (both the default named-volume path and a `BACKUP_DIR`-set bind-mount path).
- [x] `.env.example`: `BACKUP_AGE_RECIPIENT`, `BACKUP_DIR`, `BACKUP_UID`, `BACKUP_GID`, `BACKUP_MAX_AGE_HOURS`, `BACKUP_RCLONE_REMOTE`, each commented. (The optional `rclone.conf` the compose file also mounts is a plain file next to `.env`, not a seventh env var — see its own comment in `docker-compose.prod.yml`.)
- [x] `.github/workflows/ci.yml`: run `bats backup/test` and the end-to-end test (new `test-backup` job, `bash scripts/verify.sh backup`); build and push `my-finances-backup` with the same tags as backend/frontend, passing `BUILD_ID=${{ github.sha }}`.
- [x] Confirm the dev `docker-compose.yml` is untouched. (`git diff --stat docker-compose.yml` is empty.)

## Phase 4 — Backend status endpoint (test first)
- [ ] Test first: state derivation (`OK`/`STALE`/`FAILING`/`UNKNOWN`, `localOnly`) as a pure function; adapter tests for a missing, malformed and valid marker (tier per ADR 0013); controller test for `GET /api/backup-status`.
- [ ] Implement the read port (`application/`), file adapter (`infrastructure/`, `BACKUP_STATUS_PATH`), controller and DTO. No logging of marker contents beyond state and counts.
- [ ] ArchUnit rules still green (ADR 0014).
- [ ] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest`.

## Phase 5 — Frontend banner (test first)
- [ ] `npm run generate-api-types` (backend running).
- [ ] Test first: `BackupStatusBanner` with MSW — `OK`/`UNKNOWN` render nothing, `STALE`/`FAILING` warn with the last success time, `localOnly` info, per-session dismiss.
- [ ] Implement the banner and mount it in the app shell (not shown behind the F011 onboarding gate).
- [ ] `npm run lint && npm test && npm run build`.

## Phase 6 — Docs
- [ ] README: setup (keygen, `.env`, one-time `docker volume create`, `BACKUP_DIR`, optional rclone), restore, the "no authentication, VPS needs private access" notice, the key-loss warning, and a "Project status" entry.
- [ ] Root `CLAUDE.md`: external backups volume survives `down -v`; fourth service and image. Backend `CLAUDE.md`: status port and adapter.
- [ ] `CHANGELOG.md` `[Unreleased]` entry (`**F018 — Backups**`) with an `Upgrade:` sub-line; add the PR link in a follow-up commit once the PR is open.
- [ ] Tick this plan and mark F018 built in the README status.

## Verification
- [ ] `bats backup/test` green; end-to-end restore test green in CI.
- [ ] Manual, Docker Desktop on Windows: fresh `.env` with a keygen'd recipient, `docker volume create`, `up -d`; a `.dump.age` file and `status.json` appear; the banner shows nothing; stop the sidecar past the staleness window and confirm `STALE`.
- [ ] Manual: `BACKUP_DIR` pointed at a synced folder — files sync without partial uploads; `down -v` leaves the external volume and the folder intact.
- [ ] Manual, upgrade: run with a new sidecar build id, `up -d` — a `pre-upgrade` file is taken before the backend starts.
- [ ] Manual: restore onto a fresh stack with the private key; the app shows the same data; a wrong key fails before anything changes; the safety dump exists.
- [ ] Manual, host matrix: WSL2 Ubuntu and a plain Linux Docker host (ownership with `BACKUP_UID`/`BACKUP_GID`).
- [ ] Manual, optional rclone: configure a remote; a push failure shows `FAILING`; retention prunes the remote too.
- [ ] No log line or `status.json` contains an amount, description, note or entity name.
