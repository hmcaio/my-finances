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
- [x] Test first: state derivation (`OK`/`STALE`/`FAILING`/`UNKNOWN`, `localOnly`) as a pure function; adapter tests for a missing, malformed and valid marker (tier per ADR 0013 — a plain JUnit test with no Spring/Testcontainers, since the adapter touches only the filesystem, never the database); controller test for `GET /api/backup-status`.
- [x] Implement the read port (`application/`), file adapter (`infrastructure/`, `BACKUP_STATUS_PATH`), controller and DTO. No logging of marker contents beyond state and counts.
- [x] ArchUnit rules still green (ADR 0014).
- [x] `./gradlew spotlessApply` then `./gradlew spotlessCheck test integrationTest`. All green.

## Phase 5 — Frontend banner (test first)
- [x] `npm run generate-api-types` (backend running). Ran against a locally-built instance of this branch's backend; `backupStatus_getStatus`/`BackupStatusResponse` now in `schema.ts`.
- [x] Test first: `BackupStatusBanner` with MSW — `OK`/`UNKNOWN` render nothing, `STALE`/`FAILING` warn with the last success time, `localOnly` info, per-session dismiss. Dismissal is plain component state (no `sessionStorage`): the banner lives in the app shell for the page's lifetime, so a real reload is "next load" and an SPA route change never remounts it.
- [x] Implement the banner and mount it in the app shell (not shown behind the F011 onboarding gate) — `src/components/feedback/BackupStatusBanner.tsx`, mounted in `App.tsx` above the onboarding/router branch.
- [x] `npm run lint && npm test && npm run build`. All green (739 tests, 93 files, no regressions from mounting the banner globally).

## Phase 6 — Docs
- [x] README: setup (keygen, `.env`, one-time `docker volume create`, `BACKUP_DIR`, optional rclone), restore, the "no authentication, VPS needs private access" notice, the key-loss warning, and a "Project status" entry.
- [x] Root `CLAUDE.md`: external backups volume survives `down -v`; fourth service and image. Backend `CLAUDE.md`: status port and adapter.
- [x] `CHANGELOG.md` `[Unreleased]` entry (`**F018 — Backups**`) with an `Upgrade:` sub-line; add the PR link in a follow-up commit once the PR is open (no PR exists yet — left as a TODO for whoever opens it).
- [x] Tick this plan and mark F018 built in the README status.

## Verification
- [x] `bats backup/test` green (25/25, run locally against a Docker image built from `bats/bats` + `bash`/`coreutils`/`jq`, since this dev machine has no `bats` on `PATH`); `backup/test/e2e.sh` green locally against real Docker (build the real sidecar image, seed a throwaway Postgres, back up, restore into a second fresh Postgres, compare row counts/`flyway_schema_history`, wrong-key-fails-cleanly, safety-dump-exists — all passed). `ci.yml`'s new `test-backup` job runs both the same way on push/PR; not actually exercised through GitHub Actions in this session (the branch wasn't pushed).
- [ ] Manual, Docker Desktop on Windows: fresh `.env` with a keygen'd recipient, `docker volume create`, `up -d`; a `.dump.age` file and `status.json` appear; the banner shows nothing; stop the sidecar past the staleness window and confirm `STALE`. **Not done** — this requires leaving a real prod stack running for >20h (`BACKUP_MAX_AGE_HOURS`) and is a destination-state check beyond what the automated e2e test already covers; left for the user.
- [ ] Manual: `BACKUP_DIR` pointed at a synced folder — files sync without partial uploads; `down -v` leaves the external volume and the folder intact. **Not done** — needs a real Dropbox/OneDrive/Syncthing folder and a human watching it sync; left for the user. (The atomic-rename behaviour itself, and that `down -v` can't touch an `external: true` volume, are both exercised/verified by the e2e test and the `docker compose config` checks in Phase 3, respectively.)
- [x] Manual, upgrade: run with a new sidecar build id, `up -d` — a `pre-upgrade` file is taken before the backend starts. Verified directly against the built image (not full `docker compose up`, which would need real GHCR images): a container built with a different `BUILD_ID` produces `decision: forced` and a `.pre-upgrade.dump.age` file, then marks the healthcheck ready.
- [x] Manual: restore onto a fresh stack with the private key; the app shows the same data; a wrong key fails before anything changes; the safety dump exists. Verified via `backup/test/e2e.sh` and ad hoc `docker run`s against two separate throwaway Postgres containers (not the app itself, which wasn't built/running in this session) — row counts and `flyway_schema_history` matched exactly, the wrong key failed before any table existed on the target, and the safety dump was written.
- [ ] Manual, host matrix: WSL2 Ubuntu and a plain Linux Docker host (ownership with `BACKUP_UID`/`BACKUP_GID`). **Not done** — this session only has Docker Desktop for Windows available; left for the user, as the task instructions anticipated.
- [ ] Manual, optional rclone: configure a remote; a push failure shows `FAILING`; retention prunes the remote too. **Not done** — needs a real rclone remote/credentials; left for the user. (`backup.sh`'s rclone push/prune code paths exist and are exercised structurally by the script review and the local `docker compose config` check that `./rclone.conf` mounts correctly when present and is a harmless no-op when absent, but not against a real remote.)
- [x] No log line or `status.json` contains an amount, description, note or entity name. Reviewed every `log`/`echo` line in `backup/scripts/*` and the backend's `FileBackupStatusAdapter`/`BackupStatusController` path: only file names, counts, state/category words and timestamps are ever logged or written to the marker.
