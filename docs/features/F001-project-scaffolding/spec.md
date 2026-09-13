# F001 — Project Scaffolding

## Summary
Set up the repository skeleton and shared conventions every later feature builds on: backend module, frontend module, local Postgres via Docker, migration tooling, and the cross-cutting patterns required by the development guidelines (UUIDs from a single point, entity auditing, Lombok policy). No product functionality (accounts, transactions, etc.) is implemented here.

## Scope
- Backend and frontend module skeletons, buildable and runnable.
- Docker Compose for local Postgres.
- Flyway wired up with an empty baseline migration.
- Shared backend building blocks: `IdGenerator` port + adapter, `AuditableEntity` base class, JPA auditing configuration.
- Base package structure (layer-then-context).
- Out of scope: any domain entity (Account, Transaction, ...) — those are introduced by their own features (F002+), each adding its own migration and code under the structure this feature establishes.

## Backend

### Module & build
- Gradle project at `/backend`, Spring Boot (Web, Data JPA, Validation starters), Java toolchain version pinned in `build.gradle`.
- Base package: `com.chm.myfinances`.
- `version = "0.1.0"` in `build.gradle` — see [ADR 0007](../../adr/0007-single-shared-semver-and-changelog.md): backend and frontend share one SemVer version, bumped together at release time.
- Package layout (layer-then-context):
  ```
  com.chm.myfinances
  ├── domain
  │   ├── shared        // IdGenerator port, common value objects/exceptions
  │   └── <aggregate>    // one subpackage per aggregate, added by later features
  ├── application
  │   └── <aggregate>    // use case services, added by later features
  └── infrastructure
      ├── config         // JpaAuditingConfig, IdGeneratorConfig, CORS/web config
      ├── persistence
      │   ├── AuditableEntity.java
      │   └── <aggregate> // JPA entities, Spring Data repositories, adapters
      └── web
          └── <aggregate> // REST controllers, request/response DTOs
  ```
- No aggregate-specific code lives here — F002+ each add their own `domain/<aggregate>`, `application/<aggregate>`, `infrastructure/persistence/<aggregate>`, `infrastructure/web/<aggregate>` packages following this shape.

### API documentation (OpenAPI)
- `springdoc-openapi-starter-webmvc-ui` on the classpath — generates an OpenAPI 3 spec from the Spring controllers/DTOs added by F002+ with no manual spec-writing, served at `/v3/api-docs` (JSON) and browsable at `/swagger-ui.html`.
- Every feature's REST controller (F002+) picks this up automatically; no per-feature OpenAPI work is needed beyond normal Spring annotations (`@RestController`, request/response DTOs) unless a specific endpoint needs extra description via `@Operation`/`@Schema`.
- This spec doubles as the source for the frontend's typed API client generation (see Frontend below) — one contract, not two hand-maintained ones.

### IDs — single-point UUID generation
- `domain/shared/IdGenerator.java`: a port —
  ```java
  public interface IdGenerator {
      UUID newId();
  }
  ```
- `infrastructure/config/RandomUuidGenerator.java`: the only implementation, `UUID.randomUUID()`, registered as a Spring bean.
- Convention for every future aggregate: the application-layer use case (not the JPA layer, not the domain constructor itself) calls `idGenerator.newId()` and passes the id into the aggregate's factory method (e.g. `Account.open(id, ...)`). This is the "single point" — one interface, one implementation, one place (the use case) where it's invoked per creation. JPA entities never use `@GeneratedValue`; the id is already assigned when the entity is mapped for persistence.

### Auditing
- `infrastructure/persistence/AuditableEntity.java`:
  ```java
  @MappedSuperclass
  @EntityListeners(AuditingEntityListener.class)
  public abstract class AuditableEntity {

      @CreatedDate
      @Column(name = "created_at", nullable = false, updatable = false)
      private Instant createdAt;

      @LastModifiedDate
      @Column(name = "last_modified_at", nullable = false)
      private Instant lastModifiedAt;

      // getters only — these fields are framework-managed, never set by application code
  }
  ```
- `infrastructure/config/JpaAuditingConfig.java`: `@Configuration @EnableJpaAuditing` class.
- Every JPA entity introduced by later features (Account, Transaction, Transfer, Category, PaymentMethod, Budget, BudgetVersion, RecurringTemplate, RecurringTemplateVersion, InvestmentAccount, InvestmentCategory, InvestmentProduct, InvestmentBuySellLog, InvestmentSnapshot) extends `AuditableEntity`, and its Flyway migration includes `created_at timestamptz not null` and `last_modified_at timestamptz not null` columns. Value objects and embeddables (if any) are not audited — only entities with their own table/lifecycle.

### Lombok policy
- Allowed, on infrastructure-layer classes only: JPA entities (`@Getter`, `@Setter` where JPA requires mutability, `@NoArgsConstructor` for JPA, `@Builder`/`@AllArgsConstructor` for construction), and REST request/response DTOs (`@Value` or `@Getter` + `@Builder`).
- Not used on domain model classes (aggregates, entities, value objects in `domain/`). Domain classes get hand-written constructors and factory/behavior methods so invariants (e.g. a `RecurringTemplateVersion` can't be created with a null `effectiveFrom`, an `Account`'s balance math can't be bypassed) are enforced at construction/mutation time, not left to a generated all-args constructor.

### Database & migrations
- Flyway on the classpath, `spring.flyway.enabled=true`, migrations under `src/main/resources/db/migration`.
- `V1__baseline.sql`: intentionally empty/no-op placeholder (or just Flyway's own history table bootstrap) — first real schema migration belongs to F002.
- Local Postgres via Docker Compose (see Frontend/Infra section below).
- Config is split by Spring profile so environment-specific values (datasource, `server.address`) are never silently inherited across environments: `application.yml` holds only settings identical everywhere (app name, JPA/Flyway/springdoc config, `server.port`) plus `spring.profiles.default: dev` (not `.active` — `.default` yields cleanly to an explicit `SPRING_PROFILES_ACTIVE` env var, e.g. F014's `prod`, with no precedence subtlety). `application-dev.yml` carries dev's `localhost`/placeholder credentials. `application-prod.yml` (F014) and `application-test.yml` (F014, for CI) carry their own environment-specific values the same way — see [F014's spec](../F014-cicd-production-packaging/spec.md) for why this split exists (an early version had `application-prod.yml` alone, which silently inherited dev's `server.address` for anything it didn't itself override).

### Code style & formatting
- [Spotless](https://github.com/diffplug/spotless) Gradle plugin, `google-java-format`. `spotlessApply` reformats, `spotlessCheck` fails the build on unformatted code — wired into CI (F014) alongside `./gradlew test`. Chosen over Checkstyle: Spotless auto-fixes rather than just flagging violations, less rules configuration to maintain, matching this project's minimal-tooling preference (ADR 0006, ADR 0009).

### Docker Compose
- `docker-compose.yml` at repo root: one `postgres` service with a named volume for persistence (PRD §7.3 — data must survive container restarts), healthcheck, and exposed port for the backend to connect to from the host (backend itself run via `./gradlew bootRun` for now, not containerized — containerizing the backend is not required by the PRD, which only requires Postgres to persist across on-demand up/down cycles).

## Frontend

### Module & build
- `/frontend`: Vite + React + TypeScript template (`npm create vite@latest -- --template react-ts`), default scripts (`dev`, `build`, `lint` if ESLint is included by the template). Set `package.json`'s `version` to `0.1.0` (the Vite template defaults to `0.0.0`) to match the backend's starting version — ADR 0007.
- No product UI yet — a single placeholder page confirming the app boots and can reach the backend (e.g. a health-check call), removed/replaced once F002+ add real screens.
- Base folder structure to establish now (empty or near-empty, filled in by later features):
  ```
  frontend/src
  ├── api/          // typed HTTP client functions, one module per aggregate
  ├── components/   // shared/reusable UI components
  ├── features/     // one folder per feature area (accounts, transactions, ...)
  └── App.tsx
  ```

### Code style & formatting
- ESLint (extending the Vite react-ts template's baseline config) + Prettier for formatting, with `eslint-config-prettier` to disable any ESLint rules that'd conflict with Prettier. `npm run lint` fails CI (F014) on violations; a `format`/`format:check` script wraps Prettier.

### UI library (Material UI) & routing (React Router)
- `@mui/material`, `@mui/icons-material`, `@emotion/react`, `@emotion/styled`. `src/theme.ts` exports a `getTheme(mode: 'light' | 'dark')` function (one palette definition, two modes via MUI's `createTheme({ palette: { mode } })`) wrapped around the app via `ThemeProvider` + `CssBaseline` in `App.tsx`, so every later feature's components share one theme instead of ad hoc styling.
- Dark mode toggle: a small React context/hook (`useColorMode`) holding the current mode, defaulting to the OS preference (`prefers-color-scheme`) on first load and persisted to `localStorage` after that (no backend involved — this is a per-device UI preference, not product data). A toggle control lives in the shared `Layout`'s `AppBar` (see below), available on every screen.
- `react-router-dom`, `BrowserRouter` at the app root. Route tree (mounted only once onboarding, F011, has passed — see below), one path per feature area so `src/features/<area>` maps directly to a route:
  ```
  /                     → Dashboard (F012)
  /transactions         → F004
  /accounts             → F003
  /transfers            → F005
  /budgets              → F006
  /recurring            → F007
  /investments          → F008 / F009
  /settings/categories  → F002
  /settings/payment-methods → F002
  /export               → F013
  ```
- A shared `Layout` component (MUI `AppBar` + `Drawer` navigation linking to the routes above, plus the dark-mode toggle in the `AppBar`) wraps every route except onboarding.
- Onboarding (F011) is not part of the route tree — it's a top-level check in `App.tsx` ("does at least one account exist?") that renders the onboarding screen standalone when false, and the `BrowserRouter` + `Layout` + routes only when true. Keeps the "no accounts yet" case simple (no route guards/redirects to reason about) at the cost of the router not being mounted at all during onboarding — acceptable since onboarding is a one-time, single-screen flow.

### Typed API client from OpenAPI
- `npm run generate-api-types` (using `openapi-typescript` or equivalent): fetches the backend's `/v3/api-docs` and generates a TypeScript types file under `src/api/generated/` — one contract shared with the backend (see Backend § API documentation) instead of hand-typing request/response shapes on both sides and letting them drift. Re-run whenever a feature (F002+) changes an endpoint's shape; `src/api/*.ts` client functions (per-aggregate, F002+) are written against these generated types.

## Dependencies
None — this is the foundation every other feature builds on.

## Notes
- No `SecurityConfig`/auth is added anywhere (PRD §3, §7.1: no authentication, localhost-only).
- CORS between the Vite dev server and Spring Boot needs a permissive-for-localhost config (dev convenience only, still no auth).
