# F025 — Action Plan

**Depends on**: F001–F009, F013, F016, F019, F021, F022, and F024 merged first. See [ADR 0022](../../adr/0022-audit-log-explicit-port-same-transaction.md). Three PRs, each off `develop`.

## PR 1 — Infrastructure and money-moving aggregates (backend)
- [x] Write tests first for `AuditDiff` (create, update, delete, unchanged → empty, nested-free flat maps), then implement.
- [x] Write tests for `AuditRecorder` with a fake `AuditLog` port (skips empty diffs, sets origin, passes label), then implement.
- [x] `toAuditSnapshot()` on `Transaction`, `Transfer`, `Account` (tests first) and the `AuditSnapshotCompletenessTest` with per-aggregate exclude lists.
- [x] Instrument the write use cases of `Transaction`, `Transfer` and `Account` (create, update, delete, close - **not "reopen": `Account` has no reopen use case, only `close()`; the plan's original wording assumed one. `REOPEN` is `RecurringTemplate.reactivate()`'s action, instrumented in PR2**); tests assert the emitted entry with the fake port, including the cascade entries when an account is closed (its templates deactivated via `RecurringTemplateService.stop(id, SYSTEM)`, instrumented here ahead of PR2's full `RecurringTemplate` coverage since this specific cascade is a PR1 deliverable). "Same request id" is a property of the real `AuditLog` adapter reading the MDC at write time (same HTTP request), not observable from a fake-port unit test - covered instead by `AuditLogRepositoryAdapterTest`'s MDC-read-at-persist-time test plus the Verification section's manual check.
- [x] Flyway migration (next free `V` number, `V23` - `V22` was the highest when this was written): `audit_log` table, indexes, `CHECK`s. **No "migration test against pre-existing data"**: unlike the backend CLAUDE.md pattern's examples (a dedupe before a new `UNIQUE`, a backfill), this migration only adds a brand-new table with no pre-existing rows to transform, so that test shape doesn't apply here; `AuditLogRepositoryAdapterTest` covers the table's real shape (round trip, indexes implicitly exercised by filters/pagination) against the real Testcontainers Postgres instead.
- [x] JPA entity/repository/adapter for `AuditLog` (reads the request id from the MDC); integration test: `jsonb` round trip, and the audit row rolls back with a failed business write (same transaction) - and the reverse (the business write rolls back when the audit insert itself fails), both via `AuditLogTransactionalTest`/`AuditLogRepositoryAdapterTest`.
- [x] `GET /api/audit-log` with filters and pagination; controller tests; regenerate `frontend/src/api/generated/schema.ts` - **done by hand, not via the real generator**: the dev stack's fixed container names/ports (`my-finances-postgres`/`my-finances-backend`, `docker-compose.yml`) were already bound by another running session at the time this was built, so bringing up a live backend to hit `/v3/api-docs` wasn't safe here. The hand-edited `schema.ts` addition (paths, `auditLog_list` operation, `AuditLogEntryResponse`/`PagedModelAuditLogEntryResponse`/`FieldChange` schemas) type-checks (`npm run build`) but should be verified against a real regeneration once the port conflict is resolved.
- [x] ArchUnit rule: write use cases depend on the port, with an allowlist naming every not-yet-instrumented service.
- [x] Update root `CLAUDE.md`: audit rows may hold amounts/notes/names in the DB but never in logs; every new write use case calls the `AuditLog` port.
- [x] `CHANGELOG.md` entry under `[Unreleased]` → Added, with `Upgrade:` line (new Flyway migration, no action needed).

## PR 2 — Remaining aggregates (backend)
- [ ] `toAuditSnapshot()` plus instrumentation, tests first, for: Category, PaymentMethod, Institution, Budget (versioned → `UPDATE` with previous-vs-new diff, tombstone → `STOPPED`), RecurringTemplate (versioned; `active` toggle; catch-up `GENERATED` summary; confirm/skip as `USER`), Investment category/sub-category/product/holding/snapshot, Vehicle (F024). **Also `InvestmentSegment` and `AllocationPlan`/`AllocationPlanVersion`** (F026) and Transfer's `TradeConfirmation` lines (F027): both landed on `develop` after this spec/plan were written (the spec's own "(once F024 lands)" aside for `Vehicle` shows it was already tracking this kind of drift) - leaving them out would also contradict this PR's own next bullet ("the rule now covers every write use case"), since `InvestmentSegmentService`/`AllocationPlanService` would otherwise need to stay in the allowlist forever. **Note on "holding close and product delete cascades"**: re-checked against the actual code - `InvestmentProductService.delete`/`InvestmentHoldingService.delete` *reject* the operation (409) while holdings/history exist rather than cascading through them, so there is no literal SYSTEM-origin cascade entry to emit on either path (unlike the account-close → template-deactivation cascade, which is real). Each service still logs its own CLOSE/DELETE as `USER` for its own direct use case.
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
