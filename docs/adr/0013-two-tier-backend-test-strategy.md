# 0013. Backend test strategy: two tiers, real Postgres for persistence, fakes for logic

Status: Accepted
Date: 2026-09-23

## Context
[0004](0004-hexagonal-ddd-tdd.md) asks for test-first development of the rules-heavy logic and [0010](0010-testcontainers-for-backend-tests.md) settles where the database comes from. Neither says how the tests are organized, and by the time of the test audit (issues #31 and #32, PRs #34 to #40) that had drifted into an accumulation of local habits: 88 files and about 750 tests, `@SpringBootTest` boilerplate repeated per class, `persistAccount` and friends copied per class, `LocalDate.now()` in about 200 places, migration harnesses copied four times, and no way to run only the fast tests. This ADR records the conventions the audit settled on, and the options it rejected, so the next audit does not re-propose them.

## Decision
- **Two tiers, split by the JUnit tag `integration`.** `./gradlew test` is the fast tier: domain unit tests, application-layer tests against hand-written `Fake*Repository` classes, and ArchUnit rules. It needs no Docker. `./gradlew integrationTest` runs every `@SpringBootTest` class against the Testcontainers Postgres. `check`/`build` run both, and CI runs both with `--continue`. `@DatabaseIntegrationTest` and `@WebIntegrationTest` carry the tag; a `@SpringBootTest` class that does not use one of them adds `@Tag("integration")` itself, and `TestTaggingTest` fails the fast tier if one is missing.
- **What runs where.** Business rules are tested in the domain and against fakes. Anything whose correctness depends on the database (constraints, queries, transactions, migrations) or on the HTTP layer (status codes, validation, JSON shape) is an integration test against real Postgres. Fakes do not enforce constraints, so they never stand in for a persistence test.
- **No test slices.** Boot 4.x removed `@DataJpaTest` and `@AutoConfigureMockMvc`, so there is one context flavour: full `@SpringBootTest`, with a hand-built `MockMvc` for web tests. Shared context configuration keeps Spring's context cache effective.
- **Time is injected.** Production code takes a `Clock` bean (`ClockConfig`) and domain methods that depend on "today" take an explicit date. Tests that depend on time use a `MutableClock` through a `@Primary` test configuration, never the wall clock.
- **Shared test infrastructure lives in `testsupport/`**, organized by kind: `fakes/` (built on a generic `InMemoryRepository<T>`), `mothers/` (builders with defaults so a test states only what matters), `web/` (`MockMvc` and JSON helpers), `migration/` (`AbstractMigrationTest`).
- **Coverage is report-only.** JaCoCo produces a merged HTML report of both tiers, uploaded as a CI artifact. There are no thresholds, so a number can never fail the build.
- **Frontend follows the same spirit** in its own stack (shared contract helpers, named seed fixtures, a compile-time schema drift check), documented in `frontend/CLAUDE.md`.

## Alternatives considered
- **Abstract contract suites for the near-identical service tests** (audit item B11) and a **shared fake/adapter contract test** to keep fakes in sync with real adapters (part of B14). Rejected: after the shared fixtures and builders the remaining duplication is small, and an abstract suite would save lines but hide what each concrete test proves. The fake/adapter drift risk has not caused a real bug so far.
- **Parallel test execution.** Not adopted. Many tests commit real rows into the one shared container and some assert on table-wide state, so parallel forks would need a database or schema per fork. Revisit only if the roughly three-minute backend CI run becomes a problem.
- **Coverage thresholds.** Rejected: they push tests written for the number, not for a rule. Coverage stays a report to look at.
- **Separate source sets for unit and integration tests.** Rejected in favor of a tag: the tag reuses one test classpath and needs no file moves, and the meta-annotations apply it for free.

## Consequences
- The fast tier runs in seconds with no Docker; the integration tier is the one that needs it (as [0010](0010-testcontainers-for-backend-tests.md) already requires).
- `./gradlew test` alone no longer proves the database-backed behavior. Anything that needs that confidence runs `integrationTest` (or `check`), and CI always does.
- A new `@SpringBootTest` class must carry the tag; the guard test makes forgetting it a failure rather than a silent move into the fast tier.
- New real-DB fixtures go through the mothers and `testsupport` helpers rather than a per-class copy.
