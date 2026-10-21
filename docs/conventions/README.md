# Conventions

Area mechanics that the `CLAUDE.md` files disclose behind pointers. Each file holds what only work in that area needs; the rules every task needs stay in `CLAUDE.md`, review-time judgement rules are in [`../../CODING_STANDARDS.md`](../../CODING_STANDARDS.md), and mechanical rules are enforced by checks (`ArchitectureTest`, `TestConventionsTest`, ESLint, `scripts/check-versions.sh`) rather than written here. Product decisions are in `docs/PRD.md`, `docs/adr/` and `docs/features/`; stories (what changed, why) are in `git log`, PR descriptions and `spec.md`.

Add a file here when an area's mechanics would otherwise grow a `CLAUDE.md`, and add its pointer (with the trigger) to the `CLAUDE.md` that loads where the area is touched.

| File | Read when you touch |
|---|---|
| [errors.md](errors.md) | a new exception, an error body, the `errors` validation member, `apiError.ts` or its message maps |
| [auth.md](auth.md) | login, session, CSRF, CORS, `BIND_ADDR`, auth env vars, the route guard, controller-test authentication |
| [logging.md](logging.md) | a log statement, log config or retention, the request id, reading prod logs, the frontend `logger` |
| [data-constraints.md](data-constraints.md) | a new text or amount field, a limit constant, a money-precision exception |
| [tooling.md](tooling.md) | compose files, Dockerfiles, CI, `verify.sh` prerequisites, pinned-version siblings, the prod smoke test, API type generation, time zone |
| [release.md](release.md) | a user-visible change (its `CHANGELOG.md` entry), cutting a release PR, tagging a version, verifying the published images (the `release` skill points here) |
| [backend-structure.md](backend-structure.md) | a new aggregate, use-case service, repository adapter (incl. replacing child rows), query object, port, list/update endpoint, name-uniqueness check |
| [accounts-and-investments.md](accounts-and-investments.md) | account balances, net worth, investment snapshots/holdings, an `INVESTMENT` account's null opening balance, `LocalDate.MAX`/`MIN`, a new table with an FK to `accounts` |
| [versioned-history-and-recurring.md](versioned-history-and-recurring.md) | `Budget`/`RecurringTemplate`/`AllocationPlan` versions, recurring catch-up |
| [transactions-and-audit.md](transactions-and-audit.md) | `@Transactional` overloads, cascades, `AuditRecorder`/`AuditOrigin` |
| [csv-output.md](csv-output.md) | a `ReportGenerator`, CSV output, unpaged repository reads |
| [backend-testing.md](backend-testing.md) | any backend test beyond a domain/fake unit test: `@SpringBootTest`, REST, transaction-boundary, migration, concurrency, seed-row collisions |
| [frontend-structure.md](frontend-structure.md) | a route, feature folder, API area or shared module |
| [frontend-data-loading.md](frontend-data-loading.md) | a read, write, paged list, self-fetching widget, loading skeleton, load-failure state |
| [frontend-components.md](frontend-components.md) | a list page, error banner, confirmation, month field, pagination |
| [frontend-responsive.md](frontend-responsive.md) | a list table, form dialog, filter row, inline row edit, any layout that must fit 360px |
| [frontend-charts.md](frontend-charts.md) | a chart or donut |
| [frontend-i18n.md](frontend-i18n.md) | a user-visible string, translation namespace, seeded-name or enum label, formatted number/date/money |
| [frontend-testing.md](frontend-testing.md) | a Vitest test, MSW handler, seed fixture |
| [frontend-e2e.md](frontend-e2e.md) | a Playwright spec, a jsdom test that renders `Layout` |
