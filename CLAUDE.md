# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

F001 (project scaffolding) is done: `/backend` (Spring Boot/Gradle) and `/frontend` (Vite/React/TypeScript) skeletons exist and are buildable/runnable, per `docs/features/F001-project-scaffolding/`. No product functionality (accounts, transactions, ...) is implemented yet — that starts with F002.

## Build / lint / test commands

Local Postgres (required before running the backend):
```
docker compose up -d      # start (data persists in a named volume)
docker compose down       # stop
```
Includes a `pgadmin` service for local database inspection: `http://localhost:5050` (login `dev@myfinances.com` / `myfinances`; register a server with host `postgres`, port `5432`, and Postgres's own dev credentials below).

Backend (`/backend`, run from that directory):
```
./gradlew bootRun         # run the API against local Postgres (http://localhost:8080)
./gradlew build           # compile + test + package
./gradlew test            # tests only — needs Docker running (Testcontainers starts its own ephemeral Postgres, see ADR 0010)
./gradlew spotlessCheck   # formatting check (google-java-format); spotlessApply to fix
```
Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI spec: `http://localhost:8080/v3/api-docs`.

Production packaging (F014, `docker-compose.prod.yml` — a wholly separate file from dev's `docker-compose.yml`; see ADR 0006). Not part of the everyday dev loop; for a local smoke test of the prod images:
```
docker build -t ghcr.io/hmcaio/my-finances-backend:local backend
docker build -t ghcr.io/hmcaio/my-finances-frontend:local frontend
cp .env.example .env               # then set IMAGE_TAG=local
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml down -v
```
CI/CD: `.github/workflows/ci.yml` runs backend (`spotlessCheck test`, with Testcontainers providing its own ephemeral Postgres per ADR 0010) and frontend (`npm ci && npm run lint` + tests once a test runner exists) on every push/PR, and additionally builds+pushes both Docker images to GHCR on pushes to `main` and on `vX.Y.Z` tags.

Frontend (`/frontend`, run from that directory):
```
npm install               # first time (frontend/.npmrc sets legacy-peer-deps for openapi-typescript)
npm run dev                    # dev server, http://localhost:5173
npm run build                  # typecheck + production build
npm run lint                   # ESLint
npm run format / format:check  # Prettier
npm run generate-api-types     # regenerate src/api/generated/schema.ts from the backend's /v3/api-docs (backend must be running)
```

## Structural conventions

- Backend package layout is layer-then-context: `com.chm.myfinances.{domain,application,infrastructure}`, each with one subpackage per aggregate (see `docs/adr/0004-hexagonal-ddd-tdd.md`). `domain/shared` holds the `IdGenerator` port (ADR 0005); `infrastructure/persistence` holds the `AuditableEntity` base class every entity with its own table extends.
- Frontend: `src/api` (typed HTTP clients, one module per aggregate, generated types under `src/api/generated/`), `src/components` (shared UI), `src/features/<area>` (one folder per feature area, mapping to a route).
- Every commit follows Conventional Commits (`docs/adr/0009-conventional-commits.md`).
- Backend config is split by Spring profile: `application.yml` holds only settings identical across every environment, plus `spring.profiles.default: dev`. Environment-specific values (datasource, `server.address`) live only in `application-dev.yml` / `application-prod.yml` — never in the base file, so nothing environment-specific is ever silently inherited across profiles. There is no `application-test.yml`: backend tests get their datasource from a Testcontainers-provisioned Postgres via `@ServiceConnection` instead of a profile file (ADR 0010).

## Source of truth

**Read [docs/PRD.md](docs/PRD.md) before making any architectural or scope decision.** It is the authoritative, detailed spec for this system — data model (entities, versioning rules, net worth formula), functional requirements, tech stack, and explicit non-goals. Do not re-derive product decisions from first principles; the PRD already resolved many non-obvious tradeoffs (e.g. why credit card spend is a liability-increasing expense and payments are transfers, why budgets/recurring templates are versioned instead of mutated in place, why recurring-occurrence generation is lazy/catch-up rather than a real-time scheduler).

## Planned architecture (from the PRD)

- **Backend**: Java + Spring Boot, built with Gradle; Flyway for schema migrations.
- **Frontend**: React + TypeScript, built with Vite, Material UI (MUI) for components/theming, React Router for client-side routing.
- **Database**: PostgreSQL, run via Docker Compose for local dev.
- **Methodology**: Domain-Driven Design (entity clusters as aggregates — Account+Transfer, Budget+BudgetVersion, RecurringTemplate+RecurringTemplateVersion, the Investment* cluster), Hexagonal Architecture (domain/business logic isolated from Spring/JPA/Postgres behind ports), Test-Driven Design (tests-first for the rules-heavy logic: net worth calc, versioning, recurring catch-up generation).
- **Runtime model**: local, single-user, no auth, bound to `localhost` only, run on-demand (brought up/down by the user) rather than kept always-on — this is why recurring-template generation must be catch-up-based, not cron-based.

When code is added, update this file with actual build/lint/test commands and any structural conventions that emerge (package layout, module boundaries) rather than leaving this section abstract.
