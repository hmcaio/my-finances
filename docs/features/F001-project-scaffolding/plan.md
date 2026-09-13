# F001 — Action Plan

**Depends on**: none (foundation).

## Backend
- [x] Initialize Gradle project at `/backend` (Spring Boot Web, Data JPA, Validation, Flyway, Postgres driver starters), `version = "0.1.0"` (ADR 0007).
- [x] Create base package structure: `domain/shared`, `application`, `infrastructure/config`, `infrastructure/persistence`, `infrastructure/web`.
- [ ] Add `IdGenerator` port in `domain/shared` and `RandomUuidGenerator` adapter + bean wiring in `infrastructure/config`.
- [ ] Add `AuditableEntity` `@MappedSuperclass` in `infrastructure/persistence` and `JpaAuditingConfig` (`@EnableJpaAuditing`).
- [ ] Configure Flyway (`src/main/resources/db/migration`), add empty/baseline `V1__baseline.sql`.
- [x] Add `application.yml` with local Postgres connection settings.
- [ ] Add `springdoc-openapi-starter-webmvc-ui` dependency; verify `/v3/api-docs` and `/swagger-ui.html` serve.
- [ ] Add the Spotless Gradle plugin (`google-java-format`); verify `spotlessCheck`/`spotlessApply` work.
- [ ] Add a trivial health-check endpoint to verify the app boots and connects to Postgres.
- [ ] Verify `./gradlew bootRun` starts successfully against the Dockerized Postgres.

## Frontend
- [ ] Scaffold `/frontend` with Vite + React + TypeScript template; set `package.json` version to `0.1.0` to match the backend (ADR 0007).
- [ ] Establish folder structure: `src/api`, `src/components`, `src/features`.
- [ ] Configure ESLint + Prettier (`eslint-config-prettier`); verify `npm run lint` and a format-check script both work.
- [ ] Add `openapi-typescript` (or equivalent) and an `npm run generate-api-types` script pulling from the backend's `/v3/api-docs`; verify it produces `src/api/generated/` types against the health-check endpoint.
- [ ] Add MUI (`@mui/material`, `@mui/icons-material`, `@emotion/react`, `@emotion/styled`); add `src/theme.ts` (`getTheme(mode)`) and wrap `App.tsx` in `ThemeProvider` + `CssBaseline`.
- [ ] Add the `useColorMode` context/hook (OS-preference default, `localStorage` persistence) and a dark-mode toggle control.
- [ ] Add `react-router-dom`; add the route tree (per `spec.md`'s table) and a shared `Layout` component (MUI `AppBar` + `Drawer`, with the dark-mode toggle in the `AppBar`), mounted only when at least one account exists (onboarding check gates the router itself).
- [ ] Add a placeholder page that calls the backend health-check endpoint and displays the result, to confirm frontend↔backend connectivity end to end.
- [ ] Verify `npm run dev` serves the app and it successfully reaches the backend.
- [ ] Verify the dark-mode toggle switches the theme immediately and the choice survives a page reload.

## Infra
- [ ] Add `docker-compose.yml` with a `postgres` service (named volume, healthcheck, exposed port).
- [ ] Verify `docker compose up -d` then `docker compose down` preserves data across restarts (PRD §7.3).

## Verification
- [ ] From a clean checkout: `docker compose up -d`, `./gradlew bootRun`, `npm run dev` all succeed with no manual steps beyond documented ones.
- [ ] Confirm no `@GeneratedValue` usage and no domain-layer Lombok anywhere in the skeleton (nothing to check yet beyond the scaffolding itself, but worth a explicit look since this is the pattern every later feature must follow).
