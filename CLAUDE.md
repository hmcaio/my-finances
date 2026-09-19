# CLAUDE.md

Guidance for Claude Code in this repository. Cross-stack rules come first, then backend-specific and frontend-specific sections.

## Source of truth

**Read [docs/PRD.md](docs/PRD.md) before making any architectural or scope decision.** It is the authoritative spec — data model (entities, versioning rules, net worth formula), functional requirements, tech stack, explicit non-goals. Do not re-derive product decisions from first principles; the PRD already resolved many non-obvious tradeoffs (why credit card spend is a liability-increasing expense and payments are transfers, why budgets/recurring templates are versioned instead of mutated in place, why recurring-occurrence generation is lazy/catch-up rather than a scheduler).

- `docs/adr/` — architectural decisions (index: `docs/adr/README.md`).
- `docs/features/FXXX-*/{spec,plan}.md` — one folder per feature. F001–F007, F014 and F015 are built; F008–F013 are specified but not built. `plan.md` checklists and the README's "Project status" are the done-list.
- Why something changed (fixes, audits, migration renumbering) lives in `git log`, PR descriptions and the relevant `spec.md` — not in this file.

## Project shape

Local, single-user, no auth, bound to `localhost`, run on-demand rather than always-on — which is why recurring-template generation is catch-up-based, not cron-based. Java/Spring Boot + PostgreSQL/Flyway backend, React/TypeScript/Vite/MUI frontend. Domain-Driven Design, Hexagonal Architecture and test-first for the rules-heavy logic (ADR 0004).

## Commands

```
docker compose up -d      # local Postgres (+ pgAdmin at http://localhost:5050); data persists in a named volume
docker compose down
```
pgAdmin login, the pre-registered server, and the "editing `servers.json` needs `docker compose down -v`" caveat are commented in `docker-compose.yml`. Backend and frontend commands are in their own sections below.

Production packaging (F014) is a wholly separate `docker-compose.prod.yml` (ADR 0006), not part of the dev loop. Local smoke test: build `ghcr.io/hmcaio/my-finances-{backend,frontend}:local` from `backend`/`frontend`, `cp .env.example .env` with `IMAGE_TAG=local`, then `docker compose -f docker-compose.prod.yml up -d` / `down -v`.

CI (`.github/workflows/ci.yml`) runs backend `spotlessCheck test` and frontend `npm ci && npm run lint && npm test` on every push/PR, and builds+pushes both images to GHCR on `main` and `vX.Y.Z` tags.

## Workflow

- Conventional Commits (ADR 0009). Branch off `develop` and PR into `develop` (ADR 0008) — never commit to `develop` or `main` directly; don't push or open a PR unless asked.
- `/implement-feature` builds a planned feature from `docs/features/FXXX`; `/audit-and-fix` handles cross-cutting audits and fixes (issue → branch → fix with tests → verify → docs).

## Cross-stack conventions

- **Free-text fields are bounded at every layer**: a length check in the domain constructor/mutator (next to the non-blank check for mandatory fields), `@Size(max = ...)` next to `@NotBlank` on request DTOs, and a matching `varchar(n)` column (frontend description/notes inputs also set `maxLength`). All limits come from `domain/shared/TextFieldConstraints` — reuse its constants: `MAX_NAME_LENGTH` (100) for flat-taxonomy names (`Category`, `PaymentMethod`, `Account`, and future ones like F008's `InvestmentCategory`), `MAX_DESCRIPTION_LENGTH` (150, mandatory) / `MAX_ADDITIONAL_NOTES_LENGTH` (500, optional) for the description/notes pair on `Transaction`, `Transfer` and `RecurringTemplate`.
- **Money columns are `numeric(19,2)`** (`transactions.amount`, `accounts.opening_balance`, `budget_versions.monthly_cap`, ...). Reuse that precision for any new amount, and back every amount/cap positivity rule at all three layers: DTO `@Positive`, domain constructor check, DB `CHECK`.
- **The backend never sends exception text** (`spring.web.error.include-message: never`), so every expected `@ResponseStatus(CONFLICT)` case needs an explicit `conflictMessage` at the frontend API-client call site or the user just sees "Request failed with status 409".
- **API types are generated**: `npm run generate-api-types` (backend running) regenerates `frontend/src/api/generated/schema.ts` from `/v3/api-docs`. When an aggregate introduces a new `java.time` type, check its generated schema shape — springdoc mis-maps `YearMonth` as an object without `OpenApiConfig`'s `replaceWithClass(YearMonth.class, String.class)`.

## Keeping these files useful

Add a bullet only for a rule or gotcha that can't be derived from the code and would cost time to rediscover. Put the story (what changed, why, what was decided) in the commit/PR body or the feature's `spec.md`. Put a rule in the stack-specific section it applies to, or the cross-stack sections if it spans both.

## Backend (Spring Boot / Gradle)

Backend-specific rules. Cross-stack conventions are above.

### Commands (run from `backend/`)

```
./gradlew bootRun         # run the API against local Postgres (http://localhost:8080; `docker compose up -d` first)
./gradlew build           # compile + test + package
./gradlew test            # tests only — needs Docker running (Testcontainers starts its own ephemeral Postgres, ADR 0010)
./gradlew spotlessCheck   # google-java-format check; `spotlessApply` to fix (don't hand-format — it rewraps javadoc)
```
Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI spec: `http://localhost:8080/v3/api-docs`.

### Layout and shapes

- Layer-then-context packages: `com.chm.myfinances.{domain,application,infrastructure}`, one subpackage per aggregate (ADR 0004). `domain/shared` holds the `IdGenerator` port (ADR 0005 — ids never come from `@GeneratedValue` or ad hoc `UUID.randomUUID()`) and `TextFieldConstraints`. `infrastructure/persistence` holds the `AuditableEntity` base every table-backed entity extends.
- **Domain packages never import another aggregate's package.** Cross-aggregate checks (category exists and is `EXPENSE`, account is open, ...) happen in the application service via the other aggregate's repository port. Domain constructors re-check their own invariants anyway as defense in depth.
- **Use-case service**: one `@Service` per aggregate, constructor-injected with its repository port(s) and, when it creates entities, `IdGenerator`. Plain use-case method names (`create`/`rename`/`delete`/`findAll`, not generic CRUD verbs). `findById(...).orElseThrow(...)` throws `{Aggregate}NotFoundException` (`@ResponseStatus(NOT_FOUND)`) in the same package.
- **Repository adapter** (`infrastructure/persistence/<aggregate>`): package-private Spring Data `JpaRepository`, a `@Component` adapter implementing the domain port and translating via `reconstitute(...)`, and a JPA entity extending `AuditableEntity` with Lombok `@Getter`/`@Setter`/`@NoArgsConstructor` (Lombok is fine here; ADR 0005 only forbids it on domain classes).
- **Query object** for a computed-not-stored value that other features' tables feed into (`AccountBalanceQuery`, `BudgetCapQuery`, `BudgetReportQuery`, `RecurringTemplateCurrentVersionQuery`): an application-layer class beside the service, read-only.
- **Domain port for a cross-feature side effect**: an aggregate announces something through a port it owns (`domain/account/AccountClosedNotifier`) and the reacting feature implements it (`infrastructure/recurringtemplate/RealAccountClosedNotifier`), so the announcer never depends on the reactor.
- **Update endpoints** are `PATCH` with a full-replace body (every editable field required).
- **List endpoints**: `PagedModel` (`{content, page: {size, number, totalElements, totalPages}}`, Spring Data `Pageable` underneath) for tables that grow without bound (transactions, transfers); a plain list where the row count is inherently small (budgets, templates, pending occurrences).
- **Versioned history** (`Budget`+`BudgetVersion`, `RecurringTemplate`+`RecurringTemplateVersion`): edits create a new forward-only version; the effective one for month X is the latest `effectiveFrom <= X` (`resolveEffective`, pure domain logic). Editing a month that already has a version replaces it in place instead of duplicating — the service decides which, the version class only performs the replace.
- **Recurring generation is lazy/catch-up** (the app isn't always running): `RecurringOccurrenceGenerator` is a pure function; `RecurringOccurrenceCatchUpService` is the only place it touches persistence, run at startup and before the pending list is served. A pending occurrence stores only a version id and its amount is resolved live, so creating a new version must realign existing pending occurrences (`setCap` does). `runCatchUp` is deliberately *not* `@Transactional` — it's self-healing via `existsByTemplateIdAndDueDate`, and one transaction would let a broken template roll back the others.

### Errors

- One cross-cutting `@RestControllerAdvice` exists, `infrastructure/web/GlobalExceptionHandler`, and there should never be another: a single `@ExceptionHandler(Exception.class)` returning a generic 500 (`{"message": "An unexpected error occurred"}`). It rethrows anything already carrying `@ResponseStatus` or implementing Spring's `ErrorResponse`. Backed by `spring.web.error.include-{stacktrace,message,binding-errors}: never` (Boot 4.x spelling; not `server.error.*`).
- A new *expected* error case gets its own `@ResponseStatus`-annotated exception — never a new `@ExceptionHandler`. Status choice: **409** when validity depends on another aggregate's persisted state (closed account, non-`EXPENSE` category budgeted, category still referenced, duplicate name); **400** when the request is malformed regardless of state (`SameAccountTransferException`); **404** for unknown ids.
- Names are unique (`UNIQUE` on `categories`/`payment_methods`/`accounts` `name`, exact match, case-sensitive) via an application-layer `existsByName`/`existsByNameAndIdNot` check that throws `{Aggregate}NameAlreadyExistsException`. `CategoryService.delete()` rejects a category still referenced by a transaction, budget or recurring template (`CategoryInUseException`).

### Transactions

Single repository calls are self-transactional. **Any use case that performs more than one write must be `@Transactional` and opt in explicitly** — the app has no other transaction-boundary handling. A failure between two writes otherwise leaves a half-created aggregate (a template with no version) or a duplicate (a confirmed occurrence that wasn't deleted). This includes cascades across aggregates: `AccountService.close()` is `@Transactional` because it triggers template deactivation through a port, and `stop()`'s own `@Transactional` just joins it.

### Config

`application.yml` holds only settings identical across every environment plus `spring.profiles.default: dev`. Environment-specific values (datasource, `server.address`) live only in `application-dev.yml` / `application-prod.yml`, so nothing is silently inherited across profiles. There is no `application-test.yml`: tests get their datasource from Testcontainers via `@ServiceConnection` (ADR 0010).

### Testing

- **Spring Boot 4.x removed the test-slice annotations** (`@DataJpaTest`, `@WebMvcTest`, `@AutoConfigureMockMvc`, `@AutoConfigureTestDatabase`) and `TestRestTemplate`. Persistence and REST tests use full `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` (automatic rollback between tests). For REST tests build `MockMvc` by hand in `@BeforeEach` via `MockMvcBuilders.webAppContextSetup(webApplicationContext).build()` with a plain `WebApplicationContext` field and a locally-constructed `ObjectMapper` (there's no `ObjectMapper` bean). `TestcontainersConfiguration` is `public` so any package can import it.
- Application-layer logic is unit-tested against the hand-written `testsupport/Fake*Repository` fakes, no Spring context. Fakes don't enforce DB constraints, so they need the same port methods as the real repository added by hand.
- **Exception: transaction-boundary tests must NOT be `@Transactional`.** To prove a service method's `@Transactional` rolls back a multi-write use case (`*ServiceTransactionalTest`), the test must carry no class- or method-level `@Transactional`, or the service just joins the test's transaction and a same-transaction re-read still sees the un-rolled-back writes. Spy the downstream repository with `@MockitoSpyBean` on a *field* (not a method parameter), stub the *second* write to throw, then re-read through fresh repository calls. These tests commit real rows: clean up leftovers inline with a repository method you did *not* stub (`reset(spy)` first — custom derived `deleteBy...` queries need a caller-provided transaction, plain `deleteById` does not), and give fixtures with no delete port (categories/accounts/templates) unique names because they stay behind.
- **Seed rows collide with `UNIQUE` constraints in real-DB tests.** `V2` seeds categories/payment methods ("Groceries", "Rent", "Debit Card", ...) into the shared Testcontainers Postgres before any test runs, so rollback never removes them. Real-DB fixtures must not reuse those names (convention: a `" Test"` suffix) or any name a non-`@Transactional` test leaves committed.

## Frontend (React / TypeScript / Vite)

Frontend-specific rules. Cross-stack conventions are above.

### Commands (run from `frontend/`)

```
npm install                # first time (.npmrc sets legacy-peer-deps for openapi-typescript)
npm run dev                # dev server, http://localhost:5173
npm run build              # typecheck + production build
npm run lint               # ESLint
npm run format / format:check   # Prettier (write Prettier only on files you touched)
npm test                   # Vitest, non-watch, CI-friendly
npm run generate-api-types # regenerate src/api/generated/schema.ts from the running backend's /v3/api-docs
```

### Layout

`src/api` (typed HTTP clients, one module per aggregate; generated types in `src/api/generated/`), `src/components` (shared UI), `src/features/<area>` (one folder per feature area, mapping to a route), `src/utils` (framework-free helpers shared across features, e.g. `nameLookup`), `src/mocks` (MSW handlers) and `src/test` (Vitest setup).

### API clients

All clients share one Axios instance (`src/api/client.ts`) configured from `VITE_API_BASE_URL` (`.env.development` / `.env.production`). That base URL already includes `/api`, so call sites use bare relative paths (`apiClient.get('/categories')`), never a hand-built `${API_BASE_URL}/api/...`. Errors are unwrapped through `unwrap()`/`ApiError` in `src/api/apiError.ts` (Axios throws on non-2xx). A `409` needs a call-site `conflictMessage` passed to `unwrap()` — the backend never sends exception text, so without it the user sees "Request failed with status 409" (see `categories.ts`, `accounts.ts`, `budgets.ts`). `defaultErrorMessage(err, statusOverrides?)` turns a caught error into UI text; use its `statusOverrides` for a page that special-cases one status (e.g. 404).

### Shared components (`src/components`)

Use these instead of re-inlining the markup; each replaced copy-pasted code from every settings/list page.

- `ErrorAlert` — dismissible error banner; renders nothing for a `null` message. `AccountDetailPage`'s banner is intentionally *not* this (non-dismissible, different spacing).
- `InlineEditActions` — the pencil → check/✕ trio for an inline-edit table row; `editLabel` is required (labels differ per page), `saveLabel` defaults to "Save", `saving` disables save/cancel.
- `ConfirmDialog` — destructive-action confirmation (delete/close/dismiss). A dialog containing a form is not a fit; keep those custom.
- `PaginationControls` — Previous/"Page X of Y"/Next for a `PagedModel`. `onPageChange` takes a functional updater (pass `setPage` directly) so rapid clicks stay correct against React's latest state; its `sx` overrides the default embedded-list spacing for standalone pages.
- `LoadingTableRow` — placeholder row before a table's first fetch; `variant` is `'spinner'` or `'text'` (both treatments exist on purpose).

### Pages that embed other pages' widgets

A widget that fetches its own data on mount and takes no props (`PendingOccurrencesWidget`, kept prop-less so F012's dashboard can drop it in) won't refetch when the embedding page changes what it should show. The embedding page remounts it with a `key` counter it bumps after the relevant mutation (`RecurringTemplatesPage` does this after a cap edit or template creation).

### Testing

- Vitest is configured entirely through `vite.config.ts`'s `test` key (`environment: 'jsdom'`, `setupFiles: ['src/test/setup.ts']`) — there is no `vitest.config.ts`. `globals` is off, so `src/test/setup.ts` calls React Testing Library's `cleanup()` itself.
- Test files are colocated (`*.test.ts` / `*.test.tsx`) and query with accessibility-first RTL queries (`getByRole`, `getByLabelText`), not test IDs.
- MSW: one handler file per aggregate in `src/mocks/handlers/<aggregate>.ts` (mirroring `src/api`), combined in `src/mocks/handlers.ts`, served by `src/mocks/server.ts` and started/reset/closed by `src/test/setup.ts` — every test gets MSW automatically. Default handlers echo the request rather than mutating seed data, so per-test overrides use `server.use(...)`. Add a handler for every new endpoint, plus a `409` variant where it can conflict.
- `frontend/.env.test` sets `VITE_API_BASE_URL=/api` (relative, same-origin) so the Axios base URL and MSW's relative matching resolve against the same jsdom origin.
- **Test files have no Node types** (`@types/node` isn't a dependency). `process.env` in a test passes under Vitest but fails `npm run build` with TS2591 — always run the build, not just `npm test`. Pin an env var/timezone with `vi.stubEnv('TZ', ...)` and `vi.unstubAllEnvs()` in cleanup, and pin a non-UTC zone for any date/time test: CI runs in UTC, where a UTC-vs-local bug is invisible.
