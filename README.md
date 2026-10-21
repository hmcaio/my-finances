# My Finances

A personal finance manager, built for exactly one user, running on exactly one trusted machine, with no cloud, one shared login instead of accounts, and no ambiguity about who owns the data. It tracks transactions across named accounts (checking, savings, credit card, cash), enforces monthly category budgets, and computes net worth as assets minus credit card liabilities plus investments — with the accounting done properly: a credit card purchase increases what you owe, and paying the statement is a transfer between accounts rather than a second expense, so spend and debt never double-count.

Manual entry only. There's no bank sync, because there's no server to sync to — the app runs on-demand on your own machine and shuts down when you're not using it.

## Why it's built this way

Most personal finance tools are either a spreadsheet you maintain by hand, or a hosted SaaS that wants your bank credentials and your data on someone else's server. This is neither: a small, self-hosted app that runs when you tell it to and keeps everything local, with none of the multi-tenant machinery a "real" SaaS would need.

Every non-obvious decision behind that shape is written down as an [ADR](docs/adr/) rather than left implicit, and the full product spec (data model, versioning rules, net worth formula, non-goals) lives in [docs/PRD.md](docs/PRD.md). Both are meant to be read, not just archived — this README is the short version.

## Tech stack

- **Backend**: Java + Spring Boot, built with Gradle (version catalog in `backend/gradle/libs.versions.toml`). PostgreSQL via Flyway migrations. OpenAPI/Swagger UI generated automatically from the controllers.
- **Frontend**: React + TypeScript, built with Vite. Material UI for components/theming (with a dark-mode toggle), React Router for navigation, TanStack Query for every read/write against the backend ([ADR 0016](docs/adr/0016-tanstack-query-client-cache.md)), Axios for the underlying API calls through one centralized, environment-configurable client.
- **Testing**: JUnit + Testcontainers on the backend (every test run gets a real, ephemeral Postgres — no shared fixture database, no skipped tests when Docker isn't already running some other way). Vitest + React Testing Library + MSW on the frontend.
- **Infra**: Docker Compose for local dev (Postgres, pgAdmin) and a separate Compose file for a production-shaped deployment (Postgres + backend + frontend, images pulled by tag from GHCR). GitHub Actions runs tests on every push/PR and publishes versioned Docker images on release tags only (a merge to `main` publishes nothing, so `latest` is always the newest release).

## Architecture

Hexagonal Architecture, Domain-Driven Design and test-first for the rules-heavy logic (net worth, versioning, recurring catch-up) — see [docs/conventions/backend-structure.md](docs/conventions/backend-structure.md) for the package layout and conventions, [docs/PRD.md](docs/PRD.md) §5/§7.2 for the data model and aggregates, and [docs/adr/](docs/adr/) for the reasoning behind each non-obvious call.

## Repository layout

```
backend/                  Spring Boot API (domain / application / infrastructure)
frontend/                 React + TypeScript SPA
backup/                   Encrypted pg_dump sidecar image for the prod stack (F018)
scripts/                  verify.sh (what CI runs) and check-versions.sh
docs/
  PRD.md                  Product spec: data model, functional requirements, non-goals
  features/               One spec.md + plan.md per feature (F001, F002, ...), in build order
  adr/                    Architectural Decision Records
docker-compose.yml        Local dev: Postgres + pgAdmin
docker-compose.prod.yml   Production-shaped stack: Postgres + backend + frontend + backup
.github/workflows/        CI (test on every push/PR) and CD (image publish on main/tags)
CLAUDE.md                 Working guidance for AI coding agents (cross-stack; backend/, frontend/ each have their own)
```

## Getting started

Local dev needs only Docker. The default is to run everything in containers with hot reload, so the toolchain versions are the pinned ones CI and production use:

```bash
docker compose --profile full up -d   # Postgres, pgAdmin (localhost:5050), API at localhost:8080, UI at localhost:5173 (needs AUTH_USERNAME/AUTH_PASSWORD_HASH in .env, see Login)
docker compose --profile full down
```

The first start downloads Gradle and npm dependencies (a couple of minutes). Set the login credential (see [Login](#login)) and `TZ` in `.env` (see `.env.example`); `TZ` should be your own time zone so the API's "today" matches your browser's.

To run the apps natively instead (fastest loop, IDE debugging), you need a JDK 21 and the Node version in `frontend/.nvmrc` (`npm` refuses another major), and you must not run the `full` profile at the same time: both use ports 8080 and 5173. See [backend/CLAUDE.md](backend/CLAUDE.md) and [frontend/CLAUDE.md](frontend/CLAUDE.md) for the exact commands (build, lint, test, run). The short version:

```bash
docker compose up -d      # start local Postgres only (+ pgAdmin at localhost:5050)
cd backend && ./gradlew bootRun    # API at localhost:8080 (export AUTH_USERNAME / AUTH_PASSWORD_HASH in this shell first)
cd frontend && npm install && npm run dev  # UI at localhost:5173
```

Before pushing, `scripts/verify.sh` runs the same checks CI does (pinned versions, backend spotless + tests, frontend lint, format, build, tests); it needs Docker running and `npm ci` done in `frontend/`.

One-time setup: run `scripts/setup-hooks.sh` to enable the local pre-commit hook, which runs Spotless (backend) and Prettier + ESLint (frontend) on the stacks a commit touches, so formatting slips fail at commit time instead of in CI. It sets the repo-wide `core.hooksPath` (shared by all worktrees); `--uninstall` undoes it.

A production-shaped smoke test (Docker images end to end, no cloud involved) is also documented in [docs/conventions/tooling.md](docs/conventions/tooling.md).

## Running the published images (production)

To run a release rather than develop on it you need only Docker with Compose and a POSIX shell (Linux, macOS, WSL, Git Bash): no checkout, no JDK. The stack is `docker-compose.prod.yml` (Postgres, backend, frontend, backup sidecar) pulling `ghcr.io/hmcaio/my-finances-{backend,frontend,backup}:<IMAGE_TAG>` from GHCR. Pin `IMAGE_TAG` to a release (`v0.1.1`), not `latest`, so the deploy is reproducible.

```bash
TAG=v0.1.1
mkdir my-finances && cd my-finances
curl -fsSLO https://raw.githubusercontent.com/hmcaio/my-finances/$TAG/docker-compose.prod.yml
curl -fsSL  https://raw.githubusercontent.com/hmcaio/my-finances/$TAG/.env.example -o .env
```

1. Edit `.env`: `IMAGE_TAG` (to the same `$TAG`), `TZ` (your own IANA zone), `POSTGRES_PASSWORD`, `AUTH_USERNAME` and `AUTH_PASSWORD_HASH` (generate the hash from the backend image: [Login](#login); keep the single quotes). Leave `BACKUP_AGE_RECIPIENT` empty for now.
2. Choose where backups go (create the backups volume, or set `BACKUP_DIR` in `.env` and skip the volume), then generate the age keypair, in that order, and put the printed recipient in `.env` as `BACKUP_AGE_RECIPIENT`: [Backups, Setup](#backups). Store the private key off this machine before going on.
3. `docker compose -f docker-compose.prod.yml up -d`. The first start pulls the images and waits for the backup sidecar's first check; once the stack is healthy, open `http://localhost:8081` (`FRONTEND_PORT`, bound to `BIND_ADDR`; see [Reaching the app from other devices](#reaching-the-app-from-other-devices)) and sign in with the username and password you chose.

**Upgrading**: set `IMAGE_TAG` in `.env` to the new release and run `docker compose -f docker-compose.prod.yml up -d` again (read that release's `CHANGELOG.md` entry for `Upgrade:` notes first). The new backup sidecar notices its build changed and takes a `pre-upgrade` backup before the backend, and with it any new database migration, starts. That backup is recognizable by its file name, `myfinances-<timestamp>.pre-upgrade.dump.age`, and by the sidecar's log (`docker compose -f docker-compose.prod.yml logs backup` shows `decision: forced`); `status.json` has no field for it. **Rolling back** takes more than setting the old tag back, since the new release's database migrations are not undone by it: restore the `pre-upgrade` backup (see [Backups, Restoring](#backups)) and run the old tag again. That start changes the sidecar's build once more, so it takes a new forced `pre-upgrade` backup, of the restored database this time.

## Login

Every page and API call sits behind a single shared login (F036, [ADR 0029](docs/adr/0029-session-auth-and-generic-network-binding.md)): one username and one password, in dev and in prod, no bypass. Set the credential once in `.env` (gitignored; see `.env.example`) before starting any stack, dev or prod. The password itself is never stored, only its Argon2 hash:

From a checkout (contributors; needs a JDK 21):

```bash
cd backend
read -rs AUTH_PASSWORD && export AUTH_PASSWORD   # type it silently; keeps it out of shell history
./gradlew -q generateAuthHash                     # prints the AUTH_PASSWORD_HASH='...' line for .env
unset AUTH_PASSWORD
```

Running the published images, with no checkout or JDK, the same generator ships inside the backend image (use your release tag; `docker run` pulls it if needed):

```bash
read -rs AUTH_PASSWORD && export AUTH_PASSWORD   # type it silently; keeps it out of shell history
docker run --rm -e AUTH_PASSWORD --entrypoint java ghcr.io/hmcaio/my-finances-backend:v0.1.1 \
  -Dloader.main=com.chm.myfinances.infrastructure.auth.AuthHashGenerator \
  -cp app.jar org.springframework.boot.loader.launch.PropertiesLauncher   # prints the AUTH_PASSWORD_HASH='...' line for .env
unset AUTH_PASSWORD
```

`-e AUTH_PASSWORD` with no value forwards the variable from your shell, so the plaintext never appears in the command line or shell history. The generator writes the finished line (quotes included) to stdout and its notes to stderr. These commands are for a POSIX shell (Linux, macOS, WSL, Git Bash).

Without `AUTH_PASSWORD` set, `./gradlew -q generateAuthHash` prompts for the password on stdin instead (the typed text echoes there). Put the printed line in `.env` next to `AUTH_USERNAME=<your username>`, **keeping the single quotes**: the hash contains `$` characters that Docker Compose would otherwise try to expand. The backend refuses to start with a missing or malformed credential, and says which. To change the password, regenerate the hash and restart. A login session lasts 30 days idle and survives closing the browser; sessions live in backend memory, so a backend restart signs you out. Five wrong passwords in a row from one client pause login attempts for 15 minutes.

### Reaching the app from other devices

By default every published port is bound to `127.0.0.1`, so only this machine can reach the app. To reach it from a phone or laptop, set `BIND_ADDR` in `.env` to the IP of the network interface you want it on: a LAN address, or the address your private-network tool (Tailscale, WireGuard, ZeroTier, ...) gives this machine; the app doesn't care which. It applies to the dev `full` profile's backend (`:8080`) and frontend (`:5173`) and to the prod stack's nginx port (`FRONTEND_PORT`, default `8081`). Open the app at that IP (not a hostname Vite's dev server doesn't know); the login gates every request there too. Postgres and pgAdmin in `docker-compose.yml` are always published on `127.0.0.1` only, whatever `BIND_ADDR` says. TLS is not part of this: on a network you don't fully trust, terminate it in front of nginx yourself (a reverse proxy such as Caddy), and set `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` on the backend once the app is only reachable over HTTPS.

## Backups

The production stack (`docker-compose.prod.yml`) runs a `backup` sidecar that takes encrypted `pg_dump` backups on a catch-up schedule (on start if the last success is older than `BACKUP_MAX_AGE_HOURS`, then hourly while the stack is up) with GFS retention (7 daily / 5 weekly / 12 monthly). See [ADR 0015](docs/adr/0015-automated-encrypted-backups-sidecar.md) for the full design. The dev stack is never backed up — it's not in scope.

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

The private key is supplied at run time — mounted for this one-off container only — never stored in `.env`. If the `-v` mount hits path-conversion errors in Git Bash on Windows, prefix the `docker compose run` command with `MSYS_NO_PATHCONV=1` and write the host path in `C:/...` form (a `BACKUP_DIR` in that form worked too). A wrong key or a corrupt file fails before anything in the database is touched. **Restoring onto a new machine**: copy `docker-compose.prod.yml` and `.env` over, create the backups volume (or point `BACKUP_DIR` at wherever the synced folder landed on the new machine), start the stack, then run the same `restore.sh` command against whichever `.dump.age` file came with it.

Backups don't change who can reach the app: that is the login above plus the network it is published on (next section). Running the stack on a VPS instead of your own computer means making that machine private on its own — a VPN or overlay network, an SSH tunnel — and putting TLS in front of it if the network isn't already encrypted, before putting any real data on it; the login alone is a lock on the door, not a reason to publish the port to the internet.

## Project status

Built:
- **F001** — project scaffolding
- **F002** — categories & payment methods (the first real domain feature)
- **F003** — account management (`Account` CRUD, types, opening balance, running balance, closing)
- **F004** — transactions (CRUD, filtered/paginated list, running balance now driven by real activity)
- **F005** — transfers (`Transfer` between two accounts, e.g. paying a credit card statement from checking, folded into the running balance alongside transactions)
- **F006** — budgets (`Budget`+`BudgetVersion`, versioned monthly caps per expense category, budget-vs-actual reporting using each month's historically correct cap)
- **F007** — recurring templates (`RecurringTemplate`+`RecurringTemplateVersion`, lazy/catch-up pending-occurrence generation since the app isn't always running, confirm-to-transaction flow with per-occurrence overrides, auto-deactivation when the target account closes)
- **F008** — investment accounts, products and taxonomy (`INVESTMENT` accounts; a two-level category/sub-category taxonomy, fixed/migration-seeded content as of F034)
- **F009** — investment trades, snapshots and reports (manual value snapshots as the sole source of worth; buy/sell as a tagged `Transfer`; allocation view)
- **F010** — net worth (computed on read, never stored: assets + investments − credit card balances; point and trend endpoints)
- **F011** — onboarding (frontend only: a first-run, two-step flow while no account exists — create the first account, then accept/reject/add starter categories, as of F035)
- **F012** — dashboard (a grid of widgets, each fetching its own data; no composed dashboard endpoint)
- **F013** — data export (`GET /api/export`: a ZIP of per-entity CSVs) — **superseded by F030**, documented below
- **F014** — CI/CD and production packaging
- **F015** — frontend test tooling (Vitest + React Testing Library + MSW)
- **F016** — logging (SLF4J/Logback with a per-request `X-Request-Id` traced browser → nginx → backend; a frontend `logger` with error capture)
- **F017** — institutions (a shared `Institution` list every account references, replacing free-text)
- **F019** — TanStack Query migration (frontend only: every read/write through `<area>Queries` hooks, coarse invalidation)
- **F020** — Playwright e2e tooling (layout checks at mobile/tablet/desktop against mocked `/api` calls)
- **F021** — responsive layout (sidebar/drawer nav, table-to-cards lists, full-screen mobile dialogs; [ADR 0019](docs/adr/0019-responsive-layout-strategy.md))
- **F022** — investment holdings (`InvestmentHolding`, the many-to-many link between a product and the account(s) it's held in; product becomes pure taxonomy; [ADR 0020](docs/adr/0020-investment-holdings-many-to-many.md))
- **F023** — investments page refactor (allocation donuts plus Accounts/Products tabs, a server-paginated global product list)
- **F024** — fuel tracking (a `Vehicle` taxonomy; optional `FuelDetails` on `Transaction`; derived km/L, spend/km, L/km; [ADR 0021](docs/adr/0021-fuel-details-on-transaction.md))
- **F025** — audit log (every committed change recorded via an explicit `AuditLog` port in the same transaction; a read-only Activity page; [ADR 0022](docs/adr/0022-audit-log-explicit-port-same-transaction.md))
- **F026** — FII portfolio (ticker/segment taxonomy; a versioned `AllocationPlan`; dividend tracking; [ADR 0023](docs/adr/0023-fii-allocation-plan-and-dividends.md))
- **F027** — trade confirmations (a multi-line `TradeConfirmation` replacing one-product-per-transfer, with a backend-derived settlement amount; [ADR 0024](docs/adr/0024-trade-confirmations-as-multi-line-transfers.md))
- **F018** — backups (an encrypted `pg_dump` sidecar on a catch-up schedule with GFS retention; a status banner; [ADR 0015](docs/adr/0015-automated-encrypted-backups-sidecar.md))
- **F030** — reports (eleven curated, on-demand reports replacing F013's all-entity export)
- **F031** — structured error responses (RFC 9457 Problem Details body with a `type` auto-derived from the exception class, replacing the status-code-only contract; [ADR 0026](docs/adr/0026-structured-error-responses-rfc9457.md))
- **F032** — validation error details (per-field `errors` array on a bean-validation 400, extending F031's `ProblemDetail` body)
- **F033** — internationalization (English / Brazilian Portuguese UI language toggle, `react-i18next`, locale-aware number/date formatting; [ADR 0027](docs/adr/0027-frontend-i18n-react-i18next.md))
- **F034** — taxonomy lockdown (closes Institution's rename gap; removes user customization from Payment Method, Investment Category/Sub-category and Investment Segment; [ADR 0028](docs/adr/0028-fixed-taxonomy-values-and-onboarding-seeded-categories.md))
- **F035** — category onboarding (stops migration-seeding Category's regular starter rows in favor of a localized, two-step onboarding suggestion step the user can accept, reject or supplement; depends on F034's ADR 0028)
- **F036** — remote access & authentication (a single shared-credential, Argon2-hashed session-cookie login required in dev and prod, with cookie CSRF and login rate limiting; a generic `BIND_ADDR` env var for exposing the app over any private network without pinning to a specific product, with Postgres/pgAdmin pinned to loopback; [ADR 0029](docs/adr/0029-session-auth-and-generic-network-binding.md))
- **F037** — ledger (one `/ledger` page replacing Transactions and Transfers: interleaved list by month, summary, filters, accent-insensitive text search; [ADR 0030](docs/adr/0030-postgres-unaccent-for-accent-insensitive-search.md))

Documented, not built; deferred to a release after 0.1.0:
- **F028** — investment splits, backend (a product-level `InvestmentSplit` event, applied as a read-side adjustment to held units; [ADR 0025](docs/adr/0025-investment-split-as-read-side-adjustment.md))
- **F029** — investment splits, frontend (the product-detail split-history panel and record/delete dialog for F028)

See [docs/features/](docs/features/) for the full breakdown, in build order, with each feature's spec and its dependencies on the others.

## Workflow

Solo project, but run with real process because that's what keeps a project like this coherent over time rather than because a team requires it: [GitHub Flow on `main` only](docs/adr/0032-github-flow-main-only.md) (short-lived branches squash-merged into `main`, releases are a release PR plus a `vX.Y.Z` tag), [Conventional Commits](docs/adr/0009-conventional-commits.md), and a [single shared SemVer version](docs/adr/0007-single-shared-semver-and-changelog.md) for backend and frontend together, tracked in [`CHANGELOG.md`](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
