# Backend (Spring Boot / Gradle)

Backend-specific rules. Cross-stack conventions (bounded free text, `numeric(19,2)` money, error-message contract, OpenAPI) are in the root `CLAUDE.md`.

## Commands (run from `backend/`)

```
./gradlew bootRun         # run the API against local Postgres (http://localhost:8080; `docker compose up -d` first)
./gradlew build           # compile + test + package
./gradlew test            # tests only — needs Docker running (Testcontainers starts its own ephemeral Postgres, ADR 0010)
./gradlew spotlessCheck   # google-java-format check; `spotlessApply` to fix (don't hand-format — it rewraps javadoc)
```
Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI spec: `http://localhost:8080/v3/api-docs`.

## Layout and shapes

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

## Errors

- One cross-cutting `@RestControllerAdvice` exists, `infrastructure/web/GlobalExceptionHandler`, and there should never be another: a single `@ExceptionHandler(Exception.class)` returning a generic 500 (`{"message": "An unexpected error occurred"}`). It rethrows anything already carrying `@ResponseStatus` or implementing Spring's `ErrorResponse`. Backed by `spring.web.error.include-{stacktrace,message,binding-errors}: never` (Boot 4.x spelling; not `server.error.*`).
- A new *expected* error case gets its own `@ResponseStatus`-annotated exception — never a new `@ExceptionHandler`. Status choice: **409** when validity depends on another aggregate's persisted state (closed account, non-`EXPENSE` category budgeted, category still referenced, duplicate name); **400** when the request is malformed regardless of state (`SameAccountTransferException`); **404** for unknown ids.
- Names are unique (`UNIQUE` on `categories`/`payment_methods`/`accounts` `name`, exact match, case-sensitive) via an application-layer `existsByName`/`existsByNameAndIdNot` check that throws `{Aggregate}NameAlreadyExistsException`. `CategoryService.delete()` rejects a category still referenced by a transaction, budget or recurring template (`CategoryInUseException`).

## Transactions

Single repository calls are self-transactional. **Any use case that performs more than one write must be `@Transactional` and opt in explicitly** — the app has no other transaction-boundary handling. A failure between two writes otherwise leaves a half-created aggregate (a template with no version) or a duplicate (a confirmed occurrence that wasn't deleted). This includes cascades across aggregates: `AccountService.close()` is `@Transactional` because it triggers template deactivation through a port, and `stop()`'s own `@Transactional` just joins it.

## Config

`application.yml` holds only settings identical across every environment plus `spring.profiles.default: dev`. Environment-specific values (datasource, `server.address`) live only in `application-dev.yml` / `application-prod.yml`, so nothing is silently inherited across profiles. There is no `application-test.yml`: tests get their datasource from Testcontainers via `@ServiceConnection` (ADR 0010).

## Testing

- **Spring Boot 4.x removed the test-slice annotations** (`@DataJpaTest`, `@WebMvcTest`, `@AutoConfigureMockMvc`, `@AutoConfigureTestDatabase`) and `TestRestTemplate`. Persistence and REST tests use full `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` + `@Transactional` (automatic rollback between tests). For REST tests build `MockMvc` by hand in `@BeforeEach` via `MockMvcBuilders.webAppContextSetup(webApplicationContext).build()` with a plain `WebApplicationContext` field and a locally-constructed `ObjectMapper` (there's no `ObjectMapper` bean). `TestcontainersConfiguration` is `public` so any package can import it.
- Application-layer logic is unit-tested against the hand-written `testsupport/Fake*Repository` fakes, no Spring context. Fakes don't enforce DB constraints, so they need the same port methods as the real repository added by hand.
- **Exception: transaction-boundary tests must NOT be `@Transactional`.** To prove a service method's `@Transactional` rolls back a multi-write use case (`*ServiceTransactionalTest`), the test must carry no class- or method-level `@Transactional`, or the service just joins the test's transaction and a same-transaction re-read still sees the un-rolled-back writes. Spy the downstream repository with `@MockitoSpyBean` on a *field* (not a method parameter), stub the *second* write to throw, then re-read through fresh repository calls. These tests commit real rows: clean up leftovers inline with a repository method you did *not* stub (`reset(spy)` first — custom derived `deleteBy...` queries need a caller-provided transaction, plain `deleteById` does not), and give fixtures with no delete port (categories/accounts/templates) unique names because they stay behind.
- **Seed rows collide with `UNIQUE` constraints in real-DB tests.** `V2` seeds categories/payment methods ("Groceries", "Rent", "Debit Card", ...) into the shared Testcontainers Postgres before any test runs, so rollback never removes them. Real-DB fixtures must not reuse those names (convention: a `" Test"` suffix) or any name a non-`@Transactional` test leaves committed.
