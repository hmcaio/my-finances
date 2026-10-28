# Operations

Running the published images, the shared login, reaching the app from other devices, and backups and restore. This is the operator's manual; the project overview and the dev quick start are in the [root README](../README.md).

- [Running the published images (production)](#running-the-published-images-production), including [Upgrading](#upgrading)
- [Login](#login), and [Reaching the app from other devices](#reaching-the-app-from-other-devices)
- [Backups](#backups): setup, taking one now, restoring

## Running the published images (production)

To run a release rather than develop on it you need only Docker with Compose and a POSIX shell (Linux, macOS, WSL, Git Bash): no checkout, no JDK. The stack is `docker-compose.prod.yml` (Postgres, backend, frontend, backup sidecar) pulling `ghcr.io/hmcaio/my-finances-{backend,frontend,backup}:<IMAGE_TAG>` from GHCR. Images exist only for release tags: `vX.Y.Z` (stable), `vX.Y.Z-rc.N` (release candidates) and a `sha-<short>` tag naming the tagged commit; `latest` is the newest stable release and is never an unreleased commit. Pin `IMAGE_TAG` to a stable release (`vX.Y.Z`), not `latest`, so the deploy is reproducible.

```bash
TAG=vX.Y.Z   # placeholder, not a real tag (the downloads fail until you replace it): use the newest release, see https://github.com/hmcaio/my-finances/releases/latest
mkdir my-finances && cd my-finances
curl -fsSLO https://raw.githubusercontent.com/hmcaio/my-finances/$TAG/docker-compose.prod.yml
curl -fsSL  https://raw.githubusercontent.com/hmcaio/my-finances/$TAG/.env.example -o .env
```

1. Edit `.env`: `IMAGE_TAG` (to the same stable `$TAG`; `latest` always means the newest stable release, fine for a throwaway try-out but not for a reproducible deploy), `TZ` (your own IANA zone), `POSTGRES_PASSWORD`, `AUTH_USERNAME` and `AUTH_PASSWORD_HASH` (generate the hash from the backend image: [Login](#login); keep the single quotes). Leave `BACKUP_AGE_RECIPIENT` empty for now.
2. Choose where backups go (create the backups volume, or set `BACKUP_DIR` in `.env` and skip the volume), then generate the age keypair, in that order, and put the printed recipient in `.env` as `BACKUP_AGE_RECIPIENT`: [Backups, Setup](#backups). Store the private key off this machine before going on.
3. `docker compose -f docker-compose.prod.yml up -d`. The first start pulls the images and waits for the backup sidecar's first check; once the stack is healthy, open `http://localhost:8081` (`FRONTEND_PORT`, bound to `BIND_ADDR`; see [Reaching the app from other devices](#reaching-the-app-from-other-devices)) and sign in with the username and password you chose.

### Upgrading

1. Read the new release's `CHANGELOG.md` entry for `Upgrade:` notes first.
2. A release can change `docker-compose.prod.yml` and add keys to `.env.example`, so fetch both from the new release's tag. With `TAG` set to the new release (`TAG=vX.Y.Z`), download its compose file again with the same `curl -fsSLO https://raw.githubusercontent.com/hmcaio/my-finances/$TAG/docker-compose.prod.yml` as in the quickstart (it overwrites yours), and compare that tag's `.env.example` with your `.env` for new keys (the `Upgrade:` notes list them).
3. Set `IMAGE_TAG` in `.env` to the new release and run `docker compose -f docker-compose.prod.yml pull && docker compose -f docker-compose.prod.yml up -d`.

The new backup sidecar notices its build changed and takes a `pre-upgrade` backup before the backend, and with it any new database migration, starts. That backup is recognizable by its file name, `myfinances-<timestamp>.pre-upgrade.dump.age`, and by the sidecar's log (`docker compose -f docker-compose.prod.yml logs backup` shows `decision: forced`); `status.json` has no field for it.

**Rolling back** takes more than setting the old tag back, since the new release's database migrations are not undone by it: restore the `pre-upgrade` backup (see [Backups, Restoring](#backups)) and run the old tag again. That start changes the sidecar's build once more, so it takes a new forced `pre-upgrade` backup, of the restored database this time.

## Login

Every page and API call sits behind a single shared login (F036, [ADR 0029](adr/0029-session-auth-and-generic-network-binding.md)): one username and one password, in dev and in prod, no bypass. Set the credential once in `.env` (gitignored; see `.env.example`) before starting any stack, dev or prod. The password itself is never stored, only its Argon2 hash:

From a checkout (contributors; needs a JDK 25):

```bash
cd backend
read -rs AUTH_PASSWORD && export AUTH_PASSWORD   # type it silently; keeps it out of shell history
./gradlew -q generateAuthHash                     # prints the AUTH_PASSWORD_HASH='...' line for .env
unset AUTH_PASSWORD
```

Running the published images, with no checkout or JDK, the same generator ships inside the backend image (`:latest` is the newest stable release, and any release tag works too since only the generator is run; `docker run` pulls the image if needed):

```bash
read -rs AUTH_PASSWORD && export AUTH_PASSWORD   # type it silently; keeps it out of shell history
docker run --rm -e AUTH_PASSWORD --entrypoint java ghcr.io/hmcaio/my-finances-backend:latest \
  -Dloader.main=com.chm.myfinances.infrastructure.auth.AuthHashGenerator \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher   # prints the AUTH_PASSWORD_HASH='...' line for .env
unset AUTH_PASSWORD
```

`-e AUTH_PASSWORD` with no value forwards the variable from your shell, so the plaintext never appears in the command line or shell history. The generator writes the finished line (quotes included) to stdout and its notes to stderr. These commands are for a POSIX shell (Linux, macOS, WSL, Git Bash).

Without `AUTH_PASSWORD` set, `./gradlew -q generateAuthHash` prompts for the password on stdin instead (the typed text echoes there). Put the printed line in `.env` next to `AUTH_USERNAME=<your username>`, **keeping the single quotes**: the hash contains `$` characters that Docker Compose would otherwise try to expand. The backend refuses to start with a missing or malformed credential, and says which. To change the password, regenerate the hash and restart. A login session lasts 30 days idle and survives closing the browser; sessions live in backend memory, so a backend restart signs you out. Five wrong passwords in a row from one client pause login attempts for 15 minutes.

### Reaching the app from other devices

By default every published port is bound to `127.0.0.1`, so only this machine can reach the app. To reach it from a phone or laptop, set `BIND_ADDR` in `.env` to the IP of the network interface you want it on: a LAN address, or the address your private-network tool (Tailscale, WireGuard, ZeroTier, ...) gives this machine; the app doesn't care which. It applies to the dev `full` profile's backend (`:8080`) and frontend (`:5173`) and to the prod stack's nginx port (`FRONTEND_PORT`, default `8081`). Open the app at that IP (not a hostname Vite's dev server doesn't know); the login gates every request there too. Postgres and pgAdmin in `docker-compose.yml` are always published on `127.0.0.1` only, whatever `BIND_ADDR` says. TLS is not part of this: on a network you don't fully trust, terminate it in front of nginx yourself (a reverse proxy such as Caddy), and set `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` on the backend once the app is only reachable over HTTPS.

## Backups

The production stack (`docker-compose.prod.yml`) runs a `backup` sidecar that takes encrypted `pg_dump` backups on a catch-up schedule (on start if the last success is older than `BACKUP_MAX_AGE_HOURS`, then hourly while the stack is up) with GFS retention (7 daily / 5 weekly / 12 monthly). See [ADR 0015](adr/0015-automated-encrypted-backups-sidecar.md) for the full design. The dev stack is never backed up — it's not in scope.

**Setup**, once, before the first `up -d`. The order matters: Compose resolves the whole stack, backend included, even for a one-off `run --no-deps`, so `.env` must already hold `IMAGE_TAG`, `AUTH_USERNAME` and `AUTH_PASSWORD_HASH` (see [Login](#login)) and the backups location must already be settled (the volume created, or `BACKUP_DIR` set in `.env`), or the keygen command below fails (`external volume "my-finances-backups-prod" not found`).

1. Choose where backups go, now: either create the backups volume once, `docker volume create my-finances-backups-prod`, or set `BACKUP_DIR` in `.env` to a host folder (see `.env.example`) and skip the volume. A folder already synced by Dropbox/OneDrive/Syncthing/etc. gives an off-machine copy with no cloud credentials in this repo.
2. Generate an age keypair: `docker compose -f docker-compose.prod.yml run --rm --no-deps backup backup-keygen`. Put the printed recipient in `.env` as `BACKUP_AGE_RECIPIENT`. **Store the printed private key somewhere off this machine right now** — a password manager, or printed and locked away. It is never written to disk by this command and must never go in `.env` or anywhere on this host. Losing it makes every existing backup permanently unrecoverable; there is no recovery path.
3. Optional: create an `rclone.conf` (gitignored, next to `.env`) and set `BACKUP_RCLONE_REMOTE` to push the encrypted file to a remote — useful on a VPS with no synced folder of its own. A push failure counts as a backup failure in the status below.
4. `docker compose -f docker-compose.prod.yml up -d` as usual. A `myfinances-<timestamp>.dump.age` file and a `status.json` appear in the backups folder/volume after the first catch-up check. The backend reads that file read-only (`GET /api/backup-status`) and the frontend shows a banner only when something needs attention — stale, failing, or backups existing on only this one machine; nothing when everything is fine.

**Take a backup now** (for example before a risky change): the sidecar backs up only when the last success is older than `BACKUP_MAX_AGE_HOURS` (20 by default), checked at container start and then hourly, so restarting it does not force one (a new image does: the `pre-upgrade` backup above). To take one on demand:

```bash
docker compose -f docker-compose.prod.yml exec -u 1000:1000 backup backup.sh
```

Pass `-u`, with the same `BACKUP_UID:BACKUP_GID` as `.env` if you changed them from the 1000:1000 default. `exec` otherwise runs as root (the service sets no `user:`), while the sidecar's own backups run as `BACKUP_UID:BACKUP_GID` after `entrypoint.sh` drops privileges. As root, the run can fail writing its scratch file `/tmp/pg_dump.err`, left behind by those unprivileged runs, on hosts whose kernel sets `fs.protected_regular` (Docker Desktop's does); the run then records `lastError: dump_failed` and the status banner shows FAILING until the next good backup. A successful run writes the new `.dump.age` file and refreshes `status.json` exactly like a scheduled one.

Retention keeps only the newest backup of each UTC day, so an on-demand backup replaces an earlier one from the same UTC day instead of sitting beside it. That includes an upgrade's `pre-upgrade` file (it loses its retention exemption once a later ordinary backup exists), so if you might still roll back after an upgrade, copy it somewhere safe before taking another backup the same day.

**Restoring** is destructive, so it always takes a safety dump of the current database first and requires an explicit `--yes`:

```bash
docker compose -f docker-compose.prod.yml stop backend frontend
docker compose -f docker-compose.prod.yml run --rm --no-deps \
  -v /path/to/age-key.txt:/run/secrets/age-key.txt:ro \
  -e AGE_KEY_FILE=/run/secrets/age-key.txt \
  backup restore.sh myfinances-20260310T080000Z.dump.age --yes
docker compose -f docker-compose.prod.yml start backend frontend
```

The private key is supplied at run time — mounted for this one-off container only — never stored in `.env`. If the `-v` mount hits path-conversion errors in Git Bash on Windows, prefix the `docker compose run` command with `MSYS_NO_PATHCONV=1` and write the host path in `C:/...` form (a `BACKUP_DIR` in that form worked too). The same prefix is needed for any `docker compose exec` (or `docker run`) command that takes an absolute container path, such as `exec backup cat /etc/backup-build-id`, which Git Bash would otherwise rewrite to `C:/Program Files/Git/etc/...`. A wrong key or a corrupt file fails before anything in the database is touched. **Restoring onto a new machine**: copy `docker-compose.prod.yml` and `.env` over, create the backups volume (or point `BACKUP_DIR` at wherever the synced folder landed on the new machine), start the stack, then run the same `restore.sh` command against whichever `.dump.age` file came with it.

Backups don't change who can reach the app: that is the login above plus the network it is published on ([Reaching the app from other devices](#reaching-the-app-from-other-devices)). Running the stack on a VPS instead of your own computer means making that machine private on its own — a VPN or overlay network, an SSH tunnel — and putting TLS in front of it if the network isn't already encrypted, before putting any real data on it; the login alone is a lock on the door, not a reason to publish the port to the internet.
