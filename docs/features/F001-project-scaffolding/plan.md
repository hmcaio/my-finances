# F001 — Action Plan

**Depends on**: none (foundation).

## Backend
- [ ] Initialize Gradle project at `/backend` (Spring Boot Web, Data JPA, Validation, Flyway, Postgres driver starters), `version = "0.1.0"` (ADR 0007).
- [ ] Create base package structure: `domain/shared`, `application`, `infrastructure/config`, `infrastructure/persistence`, `infrastructure/web`.
- [ ] Add `IdGenerator` port in `domain/shared` and `RandomUuidGenerator` adapter + bean wiring in `infrastructure/config`.
- [ ] Add `AuditableEntity` `@MappedSuperclass` in `infrastructure/persistence` and `JpaAuditingConfig` (`@EnableJpaAuditing`).
- [ ] Configure Flyway (`src/main/resources/db/migration`), add empty/baseline `V1__baseline.sql`.
- [ ] Add `application.yml` with local Postgres connection settings.
- [ ] Add a trivial health-check endpoint to verify the app boots and connects to Postgres.
- [ ] Verify `./gradlew bootRun` starts successfully against the Dockerized Postgres.

## Frontend
- [ ] Scaffold `/frontend` with Vite + React + TypeScript template; set `package.json` version to `0.1.0` to match the backend (ADR 0007).
- [ ] Establish folder structure: `src/api`, `src/components`, `src/features`.
- [ ] Add a placeholder page that calls the backend health-check endpoint and displays the result, to confirm frontend↔backend connectivity end to end.
- [ ] Verify `npm run dev` serves the app and it successfully reaches the backend.

## Infra
- [ ] Add `docker-compose.yml` with a `postgres` service (named volume, healthcheck, exposed port).
- [ ] Verify `docker compose up -d` then `docker compose down` preserves data across restarts (PRD §7.3).

## Verification
- [ ] From a clean checkout: `docker compose up -d`, `./gradlew bootRun`, `npm run dev` all succeed with no manual steps beyond documented ones.
- [ ] Confirm no `@GeneratedValue` usage and no domain-layer Lombok anywhere in the skeleton (nothing to check yet beyond the scaffolding itself, but worth a explicit look since this is the pattern every later feature must follow).
