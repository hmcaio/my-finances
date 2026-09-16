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
CLAUDE.md                 Working guidance for AI coding agents in this repo
```

## Getting started

Local dev needs Docker (for Postgres) plus a JDK and Node toolchain for running the backend/frontend natively — see [CLAUDE.md](CLAUDE.md) for the exact, currently-accurate commands (build, lint, test, run) for both. The short version:

```bash
docker compose up -d      # start local Postgres (+ pgAdmin at localhost:5050)
cd backend && ./gradlew bootRun    # API at localhost:8080
cd frontend && npm install && npm run dev  # UI at localhost:5173
```

A production-shaped smoke test (Docker images end to end, no cloud involved) is also documented in `CLAUDE.md`.

## Project status

Built:
- **F001** — project scaffolding
- **F002** — categories & payment methods (the first real domain feature)
- **F003** — account management (`Account` CRUD, types, opening balance, running balance, closing)
- **F004** — transactions (CRUD, filtered/paginated list, running balance now driven by real activity)
- **F014** — CI/CD and production packaging
- **F015** — frontend test tooling (Vitest + React Testing Library + MSW, with real coverage backfilled for F002/F003)

Documented and next up: **F005–F013** (transfers, budgets, recurring templates, investments, net worth, onboarding, dashboard, data export) — see [docs/features/](docs/features/) for the full breakdown, in build order, with each feature's spec and its dependencies on the others.

## Workflow

Solo project, but run with real process because that's what keeps a project like this coherent over time rather than because a team requires it: [GitHub Flow with a long-lived `develop` branch](docs/adr/0008-github-flow-with-develop-branch.md) (features branch off `develop`, releases promote to `main`), [Conventional Commits](docs/adr/0009-conventional-commits.md), and a [single shared SemVer version](docs/adr/0007-single-shared-semver-and-changelog.md) for backend and frontend together, tracked in [`CHANGELOG.md`](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE).
