# Backend (Spring Boot / Gradle)

Cross-stack rules (bounded text, money, errors, auth, logging, API types) are in the root `CLAUDE.md`.

## Commands (run from `backend/`)

```
./gradlew bootRun         # run the API against local Postgres (http://localhost:8080; `docker compose up -d` first)
./gradlew test            # fast tier: domain, application (fakes) and ArchUnit tests — no Docker needed
./gradlew integrationTest # every @SpringBootTest test — needs Docker running (Testcontainers starts its own ephemeral Postgres, ADR 0010)
./gradlew spotlessCheck   # google-java-format check; `spotlessApply` to fix (don't hand-format — it rewraps javadoc)
./gradlew -q generateAuthHash  # F036: prints AUTH_PASSWORD_HASH='<argon2>' for .env; reads $AUTH_PASSWORD, else prompts on stdin
```
Swagger UI: `http://localhost:8080/swagger-ui.html`. OpenAPI spec: `http://localhost:8080/v3/api-docs`. Both need a session (login) and are dev-only: the `prod` profile turns them off (404).

## Rules for every backend task

- Packages are `com.chm.myfinances.{domain,application,infrastructure}`, one subpackage per aggregate (ADR 0004). `ArchitectureTest` fails the build on the dependency rules (listed in `docs/conventions/backend-structure.md`), so the test is the reference for them. Cross-aggregate checks (category exists and is `EXPENSE`, account is open, ...) happen in the application service through the other aggregate's repository port; domain constructors re-check their own invariants anyway.
- **A use case that performs more than one write is `@Transactional`, opted in explicitly** (the app has no other boundary handling). Every write use case also calls `AuditRecorder` in that same transaction, which makes it two writes; every externally reachable overload needs its own `@Transactional`.
- `application.yml` holds only settings identical across every environment plus `spring.profiles.default: dev`; environment-specific values (datasource, `server.address`) live in `application-dev.yml`/`application-prod.yml`, so nothing is silently inherited. There is no `application-test.yml`: tests get their datasource from Testcontainers via `@ServiceConnection` (ADR 0010).

## Conventions by area

Read the file when you touch the area.

- A new aggregate, use-case service, repository adapter (incl. replacing child rows), query object, port, list/update endpoint or name-uniqueness check → `docs/conventions/backend-structure.md`.
- Account balances, net worth, investment snapshots/holdings, an `INVESTMENT` account's null opening balance, `LocalDate.MAX`/`MIN`, a new table with an FK to `accounts` → `docs/conventions/accounts-and-investments.md`.
- `Budget`/`RecurringTemplate`/`AllocationPlan` versions or recurring catch-up → `docs/conventions/versioned-history-and-recurring.md`.
- `@Transactional` overloads, cascades, `AuditRecorder`/`AuditOrigin` → `docs/conventions/transactions-and-audit.md`.
- A `ReportGenerator` or CSV output → `docs/conventions/csv-output.md`.
- Any backend test, fakes included (a `Fake*Repository` must mirror every port method; Boot 4.x has no `@DataJpaTest`/`@WebMvcTest`; fixtures that collide with seed rows; boundary, migration, concurrency tests) → `docs/conventions/backend-testing.md`.
