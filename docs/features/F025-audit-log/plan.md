# F025 — Action Plan

**Depends on**: F001–F009, F013, F016, F019, F021, F022, and F024 merged first. See [ADR 0022](../../adr/0022-audit-log-explicit-port-same-transaction.md). Three PRs, each off `develop`.

## PR 1 — Infrastructure and money-moving aggregates (backend)
- [ ] Write tests first for `AuditDiff` (create, update, delete, unchanged → empty, nested-free flat maps), then implement.
- [ ] Write tests for `AuditRecorder` with a fake `AuditLog` port (skips empty diffs, sets origin, passes label), then implement.
- [ ] `toAuditSnapshot()` on `Transaction`, `Transfer`, `Account` (tests first) and the `AuditSnapshotCompletenessTest` with per-aggregate exclude lists.
- [ ] Instrument the write use cases of `Transaction`, `Transfer` and `Account` (create, update, delete, close, reopen); tests assert the emitted entry with the fake port, including the cascade entries when an account is closed (its templates deactivated, `SYSTEM`, same request id).
- [ ] Flyway migration (next free `V` number): `audit_log` table, indexes, `CHECK`s. Migration test against pre-existing data (backend CLAUDE.md pattern).
- [ ] JPA entity/repository/adapter for `AuditLog` (reads the request id from the MDC); integration test: `jsonb` round trip, and the audit row rolls back with a failed business write (same transaction).
- [ ] `GET /api/audit-log` with filters and pagination; controller tests; regenerate `frontend/src/api/generated/schema.ts`.
- [ ] ArchUnit rule: write use cases depend on the port, with an allowlist naming every not-yet-instrumented service.
- [ ] Update root `CLAUDE.md`: audit rows may hold amounts/notes/names in the DB but never in logs; every new write use case calls the `AuditLog` port.
- [ ] `CHANGELOG.md` entry under `[Unreleased]` → Added, with `Upgrade:` line (new Flyway migration, no action needed).

## PR 2 — Remaining aggregates (backend)
- [ ] `toAuditSnapshot()` plus instrumentation, tests first, for: Category, PaymentMethod, Institution, Budget (versioned → `UPDATE` with previous-vs-new diff, tombstone → `STOPPED`), RecurringTemplate (versioned; `active` toggle; catch-up `GENERATED` summary; confirm/skip as `USER`), Investment category/sub-category/product/holding/snapshot (holding close and product delete cascades), Vehicle (F024).
- [ ] Extend the completeness test to every aggregate.
- [ ] Empty the ArchUnit allowlist; the rule now covers every write use case.

## PR 3 — Activity page (frontend)
- [ ] `src/api/auditLog.ts`, MSW handlers, `auditLogQueries` hooks.
- [ ] Activity page: filter bar, day-grouped list in the browser's local zone, expandable diff rows, friendly per-entity field labels with raw-name fallback, mobile card layout.
- [ ] Nav entry.
- [ ] Tests: filters, expansion, label fallback, a non-UTC-zone test at a day boundary (`vi.stubEnv('TZ', ...)`), Playwright geometry check at mobile/tablet/desktop (F020).
- [ ] Docs: `README.md` status entry, `docs/features/README.md` row (already added with the spec), PRD unchanged unless the build diverged.

## Verification
- [ ] Create, edit, delete a transaction: three entries appear in Activity, newest first, with the right from/to diff; the deleted one still shows its label.
- [ ] Save a form with no changes: no new entry.
- [ ] Close an account with an active template: an account `CLOSE` entry and a `SYSTEM` template entry appear, sharing one request id.
- [ ] Edit a budget cap: one `UPDATE` with `cap 500 → 600`; stop it: `STOPPED`.
- [ ] Open the app after being away past several recurring cycles: one `GENERATED` summary per template, not one entry per occurrence.
- [ ] Force an audit insert failure (test): the business write is rolled back.
- [ ] Export a ZIP: no audit data in it.
- [ ] Nothing in the backend or browser logs contains an amount, description, note or entity name from an audit entry.
