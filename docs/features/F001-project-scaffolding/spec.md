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
- Base package: `com.myfinances` (rename is a one-line change if a different package is preferred later; not a product decision, just a placeholder).
- Package layout (layer-then-context):
  ```
  com.myfinances
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
- Local Postgres via Docker Compose (see Frontend/Infra section below) — connection settings in `application.yml` (host `localhost`, port, db name, user/password all placeholders suitable for local dev only, matching the no-auth/localhost-only scope from the PRD).

### Docker Compose
- `docker-compose.yml` at repo root: one `postgres` service with a named volume for persistence (PRD §7.3 — data must survive container restarts), healthcheck, and exposed port for the backend to connect to from the host (backend itself run via `./gradlew bootRun` for now, not containerized — containerizing the backend is not required by the PRD, which only requires Postgres to persist across on-demand up/down cycles).

## Frontend

### Module & build
- `/frontend`: Vite + React + TypeScript template (`npm create vite@latest -- --template react-ts`), default scripts (`dev`, `build`, `lint` if ESLint is included by the template).
- No product UI yet — a single placeholder page confirming the app boots and can reach the backend (e.g. a health-check call), removed/replaced once F002+ add real screens.
- Base folder structure to establish now (empty or near-empty, filled in by later features):
  ```
  frontend/src
  ├── api/          // typed HTTP client functions, one module per aggregate
  ├── components/   // shared/reusable UI components
  ├── features/     // one folder per feature area (accounts, transactions, ...)
  └── App.tsx
  ```

## Dependencies
None — this is the foundation every other feature builds on.

## Notes
- No `SecurityConfig`/auth is added anywhere (PRD §3, §7.1: no authentication, localhost-only).
- CORS between the Vite dev server and Spring Boot needs a permissive-for-localhost config (dev convenience only, still no auth).
