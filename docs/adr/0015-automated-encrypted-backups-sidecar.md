# 0015. Automated, encrypted backups run by a sidecar container in the prod stack

Status: Accepted (image base amended, [#204](https://github.com/hmcaio/my-finances/issues/204): the sidecar is built on `alpine`, not on the `postgres` image; see the last section)
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

## Amended: the sidecar no longer inherits the Postgres server image (#204)
The first design built the sidecar `FROM postgres:17-alpine` so `pg_dump`/`pg_restore` matched the server for free. That inherited three things the sidecar never uses: a Postgres server (the image was about 599 MB, larger than the backend), the base image's `VOLUME /var/lib/postgresql/data` (Docker created an anonymous volume for every sidecar container, and a plain `down` left them behind), and a bundled `gosu` whose stale Go stdlib the scan flagged (#177, [ADR 0031](0031-image-vulnerability-scan-gates-publishing.md)).

- **Now**: `FROM alpine:<full patch tag>` (the Alpine release the other images' bases are on) plus `postgresql<major>-client`, `age`, `rclone`, `gosu`, `bash`, `coreutils`, `findutils` and `jq`. The image is about 204 MB, declares no volume, and every binary is an Alpine package, so there is no bundled binary to replace.
- **The Postgres pin** moved from the base image's tag to `ARG POSTGRES_MAJOR` in `backup/Dockerfile`. `scripts/check-versions.sh` fails unless it equals the major of the `postgres` tag in the compose files and `TestcontainersConfiguration`, and checks the Alpine tag is a full patch tag. Alpine ships the client at the latest minor of the major, which dumps and restores against any server of the same major, so the client can run ahead of the server's patch tag. Dependabot's `/backup` block tracks the Alpine tag; a Postgres major bump is by hand (it was already a coordinated move).
- **Unchanged**: the scripts, the build id baked at `/etc/backup-build-id`, the entrypoint idiom (`ENTRYPOINT []` + `CMD`), the `gosu` drop to `BACKUP_UID:BACKUP_GID`, and both on-disk formats (the age-encrypted `pg_dump -Fc` file and `status.json`). A backup taken by the old image restores with the new one and the reverse, and a new image started over a folder whose marker has the old build id takes the forced `pre-upgrade` backup as before.
- **Cost**: the client version is no longer the exact server tag; if a future Alpine release drops the pinned major's client package the image build fails (visibly, in the PR), and the fix is a deliberate choice, not a silent drift.
- **Postgres 18 ([#212](https://github.com/hmcaio/my-finances/issues/212))**: `POSTGRES_MAJOR` moved to 18 with the server. The 18 image declares its `VOLUME` at `/var/lib/postgresql` (the cluster is in `18/docker`), and a 17 data volume cannot be opened by 18, so the upgrade is a restore of a backup into a fresh volume, not the usual `pre-upgrade` backup on first start: the Postgres 18 entry in `CHANGELOG.md` gives the steps.
