# My Finances

A personal finance manager, built for exactly one user, running on exactly one trusted machine, with no cloud, no login screen, and no ambiguity about who owns the data. It tracks transactions across named accounts (checking, savings, credit card, cash), enforces monthly category budgets, and computes net worth as assets minus credit card liabilities plus investments — with the accounting done properly: a credit card purchase increases what you owe, and paying the statement is a transfer between accounts rather than a second expense, so spend and debt never double-count.

Manual entry only. There's no bank sync, because there's no server to sync to — the app runs on-demand on your own machine and shuts down when you're not using it.

## Why it's built this way

Most personal finance tools are either a spreadsheet you maintain by hand, or a hosted SaaS that wants your bank credentials and your data on someone else's server. This is neither: a small, self-hosted app that runs when you tell it to and keeps everything local, with none of the multi-tenant machinery a "real" SaaS would need.

Every non-obvious decision behind that shape is written down as an [ADR](docs/adr/) rather than left implicit, and the full product spec (data model, versioning rules, net worth formula, non-goals) lives in [docs/PRD.md](docs/PRD.md). Both are meant to be read, not just archived — this README is the short version.

## Tech stack

- **Backend**: Java + Spring Boot, built with Gradle (version catalog in `backend/gradle/libs.versions.toml`). PostgreSQL via Flyway migrations. OpenAPI/Swagger UI generated automatically from the controllers.
- **Frontend**: React + TypeScript, built with Vite. Material UI for components/theming (with a dark-mode toggle), React Router for navigation, TanStack Query for every read/write against the backend ([ADR 0016](docs/adr/0016-tanstack-query-client-cache.md)), Axios for the underlying API calls through one centralized, environment-configurable client.
- **Testing**: JUnit + Testcontainers on the backend (every test run gets a real, ephemeral Postgres — no shared fixture database, no skipped tests when Docker isn't already running some other way). Vitest + React Testing Library + MSW on the frontend.
- **Infra**: Docker Compose for local dev (Postgres, pgAdmin) and a separate Compose file for a production-shaped deployment (Postgres + backend + frontend, images pulled by tag from GHCR). GitHub Actions runs tests on every push/PR and publishes versioned Docker images on `main` and on release tags.

## Architecture

Hexagonal Architecture, Domain-Driven Design and test-first for the rules-heavy logic (net worth, versioning, recurring catch-up) — see [backend/CLAUDE.md](backend/CLAUDE.md) for the package layout and conventions, [docs/PRD.md](docs/PRD.md) §5/§7.2 for the data model and aggregates, and [docs/adr/](docs/adr/) for the reasoning behind each non-obvious call.

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
docker compose --profile full up -d   # Postgres, pgAdmin (localhost:5050), API at localhost:8080, UI at localhost:5173
docker compose --profile full down
```

The first start downloads Gradle and npm dependencies (a couple of minutes). Set `TZ` in `.env` (see `.env.example`) to your own time zone so the API's "today" matches your browser's.

To run the apps natively instead (fastest loop, IDE debugging), you need a JDK 21 and the Node version in `frontend/.nvmrc` (`npm` refuses another major), and you must not run the `full` profile at the same time: both use ports 8080 and 5173. See [backend/CLAUDE.md](backend/CLAUDE.md) and [frontend/CLAUDE.md](frontend/CLAUDE.md) for the exact commands (build, lint, test, run). The short version:

```bash
docker compose up -d      # start local Postgres only (+ pgAdmin at localhost:5050)
cd backend && ./gradlew bootRun    # API at localhost:8080
cd frontend && npm install && npm run dev  # UI at localhost:5173
```

Before pushing, `scripts/verify.sh` runs the same checks CI does (pinned versions, backend spotless + tests, frontend lint, format, build, tests); it needs Docker running and `npm ci` done in `frontend/`.

A production-shaped smoke test (Docker images end to end, no cloud involved) is also documented in `CLAUDE.md`.

## Backups

The production stack (`docker-compose.prod.yml`) runs a `backup` sidecar that takes encrypted `pg_dump` backups on a catch-up schedule (on start if the last success is older than `BACKUP_MAX_AGE_HOURS`, then hourly while the stack is up) with GFS retention (7 daily / 5 weekly / 12 monthly). See [ADR 0015](docs/adr/0015-automated-encrypted-backups-sidecar.md) for the full design. The dev stack is never backed up — it's not in scope.

**Setup**, once, before the first `up -d`:

1. Generate an age keypair: `docker compose -f docker-compose.prod.yml run --rm --no-deps backup backup-keygen`. Put the printed recipient in `.env` as `BACKUP_AGE_RECIPIENT`. **Store the printed private key somewhere off this machine right now** — a password manager, or printed and locked away. It is never written to disk by this command and must never go in `.env` or anywhere on this host. Losing it makes every existing backup permanently unrecoverable; there is no recovery path.
2. Create the backups volume once: `docker volume create my-finances-backups-prod`. (Skip this if you're instead setting `BACKUP_DIR` below to a host folder — see `.env.example`.)
3. Optional: set `BACKUP_DIR` to a folder already synced by Dropbox/OneDrive/Syncthing/etc. for an off-machine copy with no cloud credentials in this repo, and/or create an `rclone.conf` (gitignored, next to `.env`) and set `BACKUP_RCLONE_REMOTE` to push the encrypted file to a remote — useful on a VPS with no synced folder of its own. A push failure counts as a backup failure in the status below.
4. `docker compose -f docker-compose.prod.yml up -d` as usual. A `myfinances-<timestamp>.dump.age` file and a `status.json` appear in the backups folder/volume after the first catch-up check. The backend reads that file read-only (`GET /api/backup-status`) and the frontend shows a banner only when something needs attention — stale, failing, or backups existing on only this one machine; nothing when everything is fine.

**Restoring** is destructive, so it always takes a safety dump of the current database first and requires an explicit `--yes`:

```bash
docker compose -f docker-compose.prod.yml stop backend frontend
docker compose -f docker-compose.prod.yml run --rm --no-deps \
  -v /path/to/age-key.txt:/run/secrets/age-key.txt:ro \
  -e AGE_KEY_FILE=/run/secrets/age-key.txt \
  backup restore.sh myfinances-20260310T080000Z.dump.age --yes
docker compose -f docker-compose.prod.yml start backend frontend
```

The private key is supplied at run time — mounted for this one-off container only — never stored in `.env`. A wrong key or a corrupt file fails before anything in the database is touched. **Restoring onto a new machine**: copy `docker-compose.prod.yml` and `.env` over, create the backups volume (or point `BACKUP_DIR` at wherever the synced folder landed on the new machine), start the stack, then run the same `restore.sh` command against whichever `.dump.age` file came with it.

**This app has no authentication** (PRD §7.3) and backups don't change that. Everything above assumes a trusted machine you control. Running the stack on a VPS instead of your own computer means that machine needs to be made private on its own — a VPN, an SSH tunnel, or an authenticated reverse proxy in front of it — before putting any real data on it; exposing it directly to the internet exposes your financial data to the internet. That's a separate, future concern this feature only documents, not solves.

## Project status

Built:
- **F001** — project scaffolding
- **F002** — categories & payment methods (the first real domain feature)
- **F003** — account management (`Account` CRUD, types, opening balance, running balance, closing)
- **F004** — transactions (CRUD, filtered/paginated list, running balance now driven by real activity)
- **F005** — transfers (`Transfer` between two accounts, e.g. paying a credit card statement from checking, folded into the running balance alongside transactions)
- **F006** — budgets (`Budget`+`BudgetVersion`, versioned monthly caps per expense category, budget-vs-actual reporting using each month's historically correct cap)
- **F007** — recurring templates (`RecurringTemplate`+`RecurringTemplateVersion`, lazy/catch-up pending-occurrence generation since the app isn't always running, confirm-to-transaction flow with per-occurrence overrides, auto-deactivation when the target account closes)
- **F008** — investment accounts, products and taxonomy (`INVESTMENT` accounts; a two-level, user-editable category/sub-category taxonomy)
- **F009** — investment trades, snapshots and reports (manual value snapshots as the sole source of worth; buy/sell as a tagged `Transfer`; allocation view)
- **F010** — net worth (computed on read, never stored: assets + investments − credit card balances; point and trend endpoints)
- **F011** — onboarding (frontend only: a first-run account-creation screen while no account exists)
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

Documented and next up:
- **F028** — investment splits, backend (a product-level `InvestmentSplit` event, applied as a read-side adjustment to held units; [ADR 0025](docs/adr/0025-investment-split-as-read-side-adjustment.md))
- **F029** — investment splits, frontend (the product-detail split-history panel and record/delete dialog for F028)
- **F031** — structured error responses (RFC 9457 Problem Details body with a `type` auto-derived from the exception class, replacing the status-code-only contract; [ADR 0026](docs/adr/0026-structured-error-responses-rfc9457.md))

See [docs/features/](docs/features/) for the full breakdown, in build order, with each feature's spec and its dependencies on the others.

## Workflow

Solo project, but run with real process because that's what keeps a project like this coherent over time rather than because a team requires it: [GitHub Flow with a long-lived `develop` branch](docs/adr/0008-github-flow-with-develop-branch.md) (features branch off `develop`, releases promote to `main`), [Conventional Commits](docs/adr/0009-conventional-commits.md), and a [single shared SemVer version](docs/adr/0007-single-shared-semver-and-changelog.md) for backend and frontend together, tracked in [`CHANGELOG.md`](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
