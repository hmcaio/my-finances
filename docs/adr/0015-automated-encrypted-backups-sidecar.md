# 0015. Automated, encrypted backups run by a sidecar container in the prod stack

Status: Accepted
Date: 2026-09-24

## Context
The prod stack (ADR 0006) keeps all data in one named Docker volume and has no backup at all. Two failures are realistic: a human or bug destroys data on the same machine (`docker compose down -v`, a bad Flyway migration on upgrade, an app bug that corrupts rows), and the disk or machine dies. The app is local, single-user and run on demand (PRD §7.3, ADR 0003), so nothing is running at 3am to trigger a cron job, and the same stack must run on Docker Desktop for Windows, WSL2 and a Linux host or VPS.

## Decision
- **A `backup` sidecar service in `docker-compose.prod.yml`**, built from its own small image (`postgres:17-alpine` + `age` + `rclone` + shell scripts) and published to GHCR by CI with the same tags as the backend and frontend.
- **Logical dumps** (`pg_dump -Fc`), not WAL archiving or volume snapshots. The dataset is tiny and single-user; point-in-time recovery is overkill and a volume tar needs the DB stopped.
- **Catch-up, not cron**: on every sidecar start it takes a backup if the last success is older than 20h, then re-checks hourly while the stack is up. Same reasoning as ADR 0003. Every start after the sidecar was (re)created by a new image is also forced and tagged `pre-upgrade`, and the backend waits for that first check, because a Flyway migration on upgrade is the likeliest data-loss event.
- **GFS retention** (7 daily, 5 weekly, 12 monthly), pruned by calendar bucket rather than by file count so irregular on-demand usage can't push out the only old backup.
- **Encrypted with an `age` public key** held in `.env`; the private key is kept off the host, so a stolen `.env` or compromised host can't read backups.
- **Storage**: a `BACKUP_DIR` bind mount when set (point it at a synced folder for an off-machine copy), otherwise an `external` named volume that `docker compose down -v` cannot remove. An optional `rclone` push of the encrypted file covers hosts with no synced folder (VPS).
- **Status is a JSON marker** the sidecar writes next to the dumps; the backend reads it through a read-only mount and the frontend warns when the last success is stale or when backups exist only on this machine.
- **Restore is a script** run as a one-off container, which always takes a safety dump first and needs an explicit `--yes`.

## Consequences
- Off-machine safety depends on the operator setting `BACKUP_DIR` to a synced folder or configuring `rclone`; without either, the UI says so instead of pretending.
- The backups volume is deliberately outside the compose lifecycle: it must be created once (`docker volume create my-finances-backups-prod`) and is not removed by `down -v`. That is the point, and also a footgun for anyone expecting `down -v` to leave nothing behind.
- Losing the age private key makes every backup unrecoverable. The `backup-keygen` helper and the docs state this loudly; nothing can recover it.
- The sidecar never has the decrypt key, so it cannot run the automated restore-verification a passphrase model would allow. Restore is tested in CI with a throwaway key pair instead.
- The sidecar image must be built and versioned with the other two images; CI grows a third image.
- The stack still has no authentication (PRD §7.3). Backups don't change that: running it on a VPS needs private access (VPN, SSH tunnel, authenticated reverse proxy), and that is a separate future feature, not part of this decision.
- Out of scope, and not precluded: PITR/WAL, dev-stack backups, UI-triggered backup or restore (the backup trigger is a plain script so an endpoint could call it later), automated periodic restore tests.
