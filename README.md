# My Finances

A personal finance manager, built for exactly one user, running on exactly one trusted machine, with no cloud, no login screen, and no ambiguity about who owns the data. It tracks transactions across named accounts (checking, savings, credit card, cash), enforces monthly category budgets, and computes net worth as assets minus credit card liabilities plus investments — with the accounting done properly: a credit card purchase increases what you owe, and paying the statement is a transfer between accounts rather than a second expense, so spend and debt never double-count.

Manual entry only. There's no bank sync, because there's no server to sync to — the app runs on-demand on your own machine and shuts down when you're not using it.

## Why it's built this way

Most personal finance tools are either a spreadsheet you maintain by hand, or a hosted SaaS that wants your bank credentials and your data on someone else's server. This is neither: a small, self-hosted app that runs when you tell it to, keeps everything local, and gets the financial modeling right (versioned budgets and recurring bills, correct credit-card accounting, an honest net worth number) without any of the multi-tenant complexity — auth, roles, rate limiting, horizontal scaling — that a "real" SaaS would need and this project has no use for.

Every non-obvious decision behind that shape is written down as an [ADR](docs/adr/) rather than left implicit, and the full product spec lives in [docs/PRD.md](docs/PRD.md). Both are meant to be read, not just archived — this README is the short version.

## Tech stack

- **Backend**: Java + Spring Boot, built with Gradle (version catalog in `backend/gradle/libs.versions.toml`). PostgreSQL via Flyway migrations. OpenAPI/Swagger UI generated automatically from the controllers.
- **Frontend**: React + TypeScript, built with Vite. Material UI for components/theming (with a dark-mode toggle), React Router for navigation, Axios for API calls through one centralized, environment-configurable client.
- **Testing**: JUnit + Testcontainers on the backend (every test run gets a real, ephemeral Postgres — no shared fixture database, no skipped tests when Docker isn't already running some other way). Vitest + React Testing Library + MSW on the frontend.
- **Infra**: Docker Compose for local dev (Postgres, pgAdmin) and a separate Compose file for a production-shaped deployment (Postgres + backend + frontend, images pulled by tag from GHCR). GitHub Actions runs tests on every push/PR and publishes versioned Docker images on `main` and on release tags.

## Architecture

The backend follows Hexagonal Architecture and Domain-Driven Design: `domain/` holds framework-free business logic and repository *ports*, `application/` holds use-case services, and `infrastructure/` holds everything that talks to Spring, JPA, or the outside world — REST controllers, JPA entities, adapters implementing the domain's ports. Entity clusters map to aggregates (`Account`+`Transfer`, `Budget`+`BudgetVersion`, `RecurringTemplate`+`RecurringTemplateVersion`, the `Investment*` group), each enforcing its own invariants at construction/mutation time rather than relying on a validation layer bolted on afterward.

Development is test-driven for the logic that's actually rules-heavy — net worth calculation, versioning resolution, the lazy/catch-up generation that recurring bills need because the app isn't always running — rather than dogmatically for every line of CRUD.

A few decisions worth knowing up front because they shape a lot of the code:

- **IDs** are UUIDs generated from a single point (`IdGenerator` port), not database defaults — an aggregate has its identity before it's ever persisted.
- **Budgets and recurring templates are versioned**, not mutated in place, so a cap or amount change never silently rewrites what a past month's report showed.
- **Every persisted entity is audited** (`created_at`/`last_modified_at`) via a shared base class.
- **Config is split by Spring profile** (`dev`/`prod`), with the shared `application.yml` holding nothing environment-specific — so an environment-specific value is never silently inherited from the wrong profile.

The full list of these decisions, with the reasoning behind each, is in [docs/adr/](docs/adr/).

## Repository layout

```
backend/                  Spring Boot API (domain / application / infrastructure)
frontend/                 React + TypeScript SPA
docs/
  PRD.md                  Product spec: data model, functional requirements, non-goals
  features/               One spec.md + plan.md per feature (F001, F002, ...), in build order
  adr/                    Architectural Decision Records
docker-compose.yml        Local dev: Postgres + pgAdmin
docker-compose.prod.yml   Production-shaped stack: Postgres + backend + frontend
.github/workflows/        CI (test on every push/PR) and CD (image publish on main/tags)
CLAUDE.md                 Working guidance for AI coding agents (cross-stack; backend/ and frontend/ each have their own)
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

## Project status

Built:
- **F001** — project scaffolding
- **F002** — categories & payment methods (the first real domain feature)
- **F003** — account management (`Account` CRUD, types, opening balance, running balance, closing)
- **F004** — transactions (CRUD, filtered/paginated list, running balance now driven by real activity)
- **F005** — transfers (`Transfer` between two accounts, e.g. paying a credit card statement from checking, folded into the running balance alongside transactions)
- **F006** — budgets (`Budget`+`BudgetVersion`, versioned monthly caps per expense category, budget-vs-actual reporting using each month's historically correct cap)
- **F007** — recurring templates (`RecurringTemplate`+`RecurringTemplateVersion`, lazy/catch-up pending-occurrence generation since the app isn't always running, confirm-to-transaction flow with per-occurrence overrides, auto-deactivation when the target account closes)
- **F008** — investment accounts, products and taxonomy (`INVESTMENT` accounts with no opening balance that take no transactions or recurring templates; a two-level, user-editable investment category/sub-category taxonomy seeded with Brazilian defaults; `InvestmentProduct`s inside those accounts, closed instead of deleted once they have history. Snapshots, buy/sell transfers and the allocation view are F009)
- **F009** — investment trades, snapshots and reports (`InvestmentSnapshot`, the manually entered value of a product and the sole source of its worth; buys and sells as `Transfer`s tagged with a product, with record-only quantity, unit price and taxes and an optional resulting balance that records the snapshot in the same step; a `needsSnapshot` flag and a close guard that keep trades and snapshots in step; an `INVESTMENT` account's balance as the sum of its products' latest snapshots; the allocation chart with category to sub-category drill-down and a monthly per-product value series)
- **F010** — net worth (`NetWorthQuery`, computed on read and never stored: asset plus investment account balances minus credit card balances as of a date, counting each account only from its opening date until its closed date so closing an account leaves past months unchanged; a point endpoint and a trend endpoint sampled at every change date or at each month-end; a trend chart with a monthly/every-change toggle)
- **F011** — onboarding (frontend only: while no account exists, closed ones included, the app shows a first-run screen with the account form instead of the app shell; nothing is persisted, so the state can't drift from the data)
- **F012** — dashboard (a fixed grid composing the widgets other features own, each fetching its own data: this month's spend by category (a new `GET /api/transactions/spend-by-category`), budget-vs-actual bars, open account balances, the net worth trend, the investment allocation and the pending recurring bills, whose confirm refreshes the widgets it affects; no composed dashboard endpoint)
- **F013** — data export (`GET /api/export`: a ZIP of thirteen CSVs, one per entity, every foreign key as `_id` plus `_name`, optionally narrowed by date range, account and category, each filter touching only the files that have that dimension; text cells are guarded against spreadsheet formulas; an Export page downloads it) — **superseded by F030**, documented below
- **F014** — CI/CD and production packaging
- **F015** — frontend test tooling (Vitest + React Testing Library + MSW, with real coverage backfilled for F002/F003)
- **F016** — logging (SLF4J/Logback with a per-request `X-Request-Id` traced browser → nginx → backend, a size- and age-capped rolling backend log that survives `docker compose down`, a frontend `logger` with HTTP/uncaught-error capture and a page error boundary; ids and counts only, never amounts or descriptions)
- **F017** — institutions (a shared `Institution` list with a built-in "No institution" row that can be renamed but not deleted; every account references exactly one, replacing the old free-text `institution`, which migration `V12` converts; picked or created inline via a shared `InstitutionSelect`)
- **F019** — TanStack Query migration (frontend only: every read and write goes through per-area `<area>Queries` hooks over TanStack Query v5, replacing `useAsyncData`/`usePagedData`; shared reads are fetched once per staleness window, and any successful write invalidates every query so no view shows stale money; the pending-occurrences read is never cached because it triggers lazy catch-up; no backend or server-cache change)
- **F020** — Playwright e2e tooling (frontend only: layout checks in Chromium at mobile, tablet and desktop widths against the Vite dev server with every `/api` call mocked from the existing `src/mocks` fixtures; DOM and geometry assertions only, no screenshot baselines; a smoke spec and its own CI job; groundwork for F021)
- **F021** — responsive layout (frontend only: a permanent sidebar on wide screens and a menu button opening a slide-in drawer on tablets/phones; a list with 3+ columns becomes cards below `sm` and hides low-priority columns behind a per-row chevron on tablet, `ResponsiveTable`; add/edit forms open in `ResponsiveDialog` — full screen on phones — instead of a panel below the list; `ResponsiveFilterBar` collapses filters behind a button with an active-count badge on phones; larger tap targets on touch devices; every page verified with the F020 Playwright suite; [ADR 0019](docs/adr/0019-responsive-layout-strategy.md))
- **F022** — investment holdings (`InvestmentHolding`, the many-to-many link between an `InvestmentProduct` and the `INVESTMENT` account(s) it's held in, so the same instrument at two brokers is one product with two holdings instead of two duplicate rows; `InvestmentProduct` becomes pure taxonomy with a globally unique name, and `closedDate`/the close guard/`needsSnapshot`/`InvestmentSnapshot`'s key all move to the holding; account balance, allocation and the value series sum across a product's holdings; prerequisite for F023; [ADR 0020](docs/adr/0020-investment-holdings-many-to-many.md))
- **F023** — investments page refactor (the Investments page becomes a dashboard: three allocation donuts — category, keeping its click-to-drill into sub-categories, plus two new flat ones, sub-category and account — above an "Accounts" tab (today's table, unchanged) and a new "Products" tab, a server-paginated global product list filterable by category, sub-category, account and a derived Open/Closed/All status and searchable by name, linking out to the product detail page; `GET /api/investments/allocation` gains `groupBy=ACCOUNT` and `GET /api/investment-products` becomes a `PagedModel`)
- **F024** — fuel tracking (a `Vehicle` taxonomy; `Transaction` gains an optional `FuelDetails` — vehicle, fuel type, liters, price per liter, odometer, km since last fill — present if and only if its category is a dedicated `fuel_category` flag, independent of `built_in`; km/L, spend/km and L/km computed on read (`FuelRatiosQuery`), never stored; a Fuel page with a per-vehicle transaction list and three hand-drawn per-fill time-series charts; `transactions.csv` export gains the fuel columns; [ADR 0021](docs/adr/0021-fuel-details-on-transaction.md))
- **F025** — audit log (every committed create, update, delete, close, reopen and stop on every aggregate recorded in an append-only `audit_log` table through an explicit `AuditLog` port called in the same transaction as the change, `jsonb` field-by-field before/after diff, `USER`/`SYSTEM` origin — a lazy recurring catch-up run records one `GENERATED` summary per template instead of one entry per occurrence, and a cascade such as closing an account deactivating its templates records its own `SYSTEM` entries under the same request id; a read-only Activity page lists every entry, newest first, grouped by day in the viewer's local time zone, filterable by date range/entity type/action/origin, each row expanding to its diff; not part of the data export, no undo, no retention; [ADR 0022](docs/adr/0022-audit-log-explicit-port-same-transaction.md))
- **F026** — FII portfolio (a `ticker` and a new, independent `InvestmentSegment` taxonomy on `InvestmentProduct`; a versioned `AllocationPlan`/`AllocationPlanVersion`/`AllocationPlanEntry` (percent per FII, same shape as a budget, entries summing to exactly 100%); a dedicated FII page with a portfolio list (cotas held/amount contributed computed from trades, never price-derived), an allocation-plan editor and two nested (two-ring) allocation donuts, Actual and Planned, segment inner ring/ticker outer ring; dividend tracking via a dedicated `dividend_category` on `Category` and `Transaction.investmentHoldingId`, a Register Dividend form and a history view grouped by ticker/month; `investment_segments.csv`/`allocation_plan_entries.csv` added to the data export, `investment_products.csv`/`transactions.csv` gain their new columns; [ADR 0023](docs/adr/0023-fii-allocation-plan-and-dividends.md))
- **F027** — trade confirmations (generalizes a buy/sell `Transfer` from one product per transfer to a multi-line `TradeConfirmation` matching a real *nota de negociação* — one cash settlement, one aggregate tax figure, one-or-more product lines, mixed buy/sell and partial fills allowed; the backend derives and enforces the settlement amount/direction from the lines via `TradeConfirmation.netCost`, rejecting an exact-zero net; per-line optional `resultingBalance`/`closeHolding`; `FiiPortfolioQuery`/`InvestmentValueSeriesQuery`/`InvestmentSnapshotFreshnessQuery` and the holding-history checker reworked onto the new `transfer_trade_lines` table; a new `GET /api/trade-confirmation-lines?productId=` for a product's own line history; `transfers.csv` export loses its flat trade columns in favor of a new `transfer_trade_lines.csv`; [ADR 0024](docs/adr/0024-trade-confirmations-as-multi-line-transfers.md))

Documented and next up: **F018** (automated encrypted backups for the prod stack), **F028** (investment splits, backend — a product-level `InvestmentSplit` event for a stock/FII split or reverse split, and the shared read-side adjustment that corrects the cotas-held total and the monthly value series' `units` without ever rewriting a recorded trade; [ADR 0025](docs/adr/0025-investment-split-as-read-side-adjustment.md)), **F029** (investment splits, frontend — the product-detail-page split history panel and record/delete dialog for F028, split out as a separate feature at the user's request) and **F030** (reports — eleven curated, on-demand reports replacing F013's all-entity export now that F018 covers disaster recovery: transactions & transfers, account balances, FII portfolio, fuel history & stats, budgets & spending by category, investments, net worth trend, recurring templates/fixed costs, investment allocation by institution/segment, dividend income, annual summary) — see [docs/features/](docs/features/) for the full breakdown, in build order, with each feature's spec and its dependencies on the others.

## Workflow

Solo project, but run with real process because that's what keeps a project like this coherent over time rather than because a team requires it: [GitHub Flow with a long-lived `develop` branch](docs/adr/0008-github-flow-with-develop-branch.md) (features branch off `develop`, releases promote to `main`), [Conventional Commits](docs/adr/0009-conventional-commits.md), and a [single shared SemVer version](docs/adr/0007-single-shared-semver-and-changelog.md) for backend and frontend together, tracked in [`CHANGELOG.md`](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
