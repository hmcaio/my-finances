# F025 — Audit Log

## Summary
Adds a user actions log: every committed write on every aggregate is recorded in an append-only `audit_log` table, with a before/after field diff, and surfaced on a read-only Activity page (PRD §5.12, §6.12). Capture is an explicit `AuditLog` port called from application services in the same transaction as the change ([ADR 0022](../../adr/0022-audit-log-explicit-port-same-transaction.md)).

## Scope
- New `audit_log` table and `AuditLog` output port, called from every write use case of every aggregate: Category, PaymentMethod, Institution, Account, Transaction, Transfer, Budget, RecurringTemplate, Investment category/sub-category/product/holding/snapshot, and (once F024 lands) Vehicle. **Also `InvestmentSegment` and `AllocationPlan`/`AllocationPlanVersion` (F026) and Transfer's `TradeConfirmation` lines (F027)**: both landed on `develop` after this spec was written, same drift this bullet's own "(once F024 lands)" aside was already tracking - covering them keeps the "every write use case" claim true and lets PR2 empty the ArchUnit allowlist.
- Actions: `CREATE`, `UPDATE`, `DELETE`, `CLOSE`, `REOPEN`, `STOPPED` (budget tombstone), `GENERATED` (recurring catch-up summary). Origin: `USER` or `SYSTEM`.
- A generic before/after diff computed from per-aggregate `toAuditSnapshot()`.
- `GET /api/audit-log` (paginated, newest first, filterable) and an Activity page.
- Out of scope (non-goals): undo/revert from the log; export (the log is not part of the F013 ZIP); retention or pruning; tamper-proofing; multi-user attribution (no `actor`, only `origin`); entity-scoped "history" links from account/transaction pages (a follow-up); backfill of pre-existing data; logging reads, navigation, rejected requests or no-op updates.

## Decisions
See [ADR 0022](../../adr/0022-audit-log-explicit-port-same-transaction.md) for the full rationale. Summary:
- **Explicit port, same transaction** — the audit insert commits or rolls back with the change; if it fails, the write fails.
- **Snapshot diff, not reflection** — each aggregate decides its audited fields; a completeness test with an explicit exclude list catches silent gaps.
- **Versioned entities log as `UPDATE`** on the logical entity, diffed between the previous and the new version; a tombstone logs `STOPPED`.
- **Recurring catch-up logs one `GENERATED` summary per template per run** (`SYSTEM`); confirm and skip log individually as `USER`.
- **Cascades log their own `SYSTEM` entries** under the same `request_id` (closing an account deactivates its templates; closing/deleting an investment product touches its holdings).
- **No-op updates (empty diff) are not logged.**
- **`entity_label` is stored in the DB only**, never written to a log line (ADR 0011).

## Backend

### Domain / application
- `application/auditlog/AuditLog.java`: output port. `record(AuditEntry)`.
- `application/auditlog/AuditEntry.java`: `entityType` (enum), `entityId`, `entityLabel`, `action` (enum), `origin` (enum), `changes` (`Map<String, FieldChange>`), `requestId` (nullable; taken from the MDC by the adapter, not by callers).
- `application/auditlog/AuditDiff.java`: pure function `diff(Map<String,Object> before, Map<String,Object> after)` → `Map<String, FieldChange>` (`from`, `to`). For `CREATE` the `from` side is absent; for `DELETE` the `to` side is absent. Returns empty when nothing changed (the caller then skips the write).
- Each auditable aggregate gains `toAuditSnapshot()` returning a flat `Map<String,Object>` of primitives/strings (money as plain decimals, dates as ISO strings, references as ids). A shared `AuditSnapshotCompletenessTest` checks, per aggregate, that every persisted field appears in the snapshot or in that aggregate's explicit exclude list (`id`, technical timestamps).
- Every write use case snapshots before, applies the change, snapshots after, calls `auditLog.record(...)` via a small helper (`AuditRecorder`) that does the diff, skips empty diffs and fills `origin`. Use cases stay `@Transactional`; F025 adds no new transaction boundaries.
- `AuditRecorder` also resolves a reference field's id to the referenced entity's label via `AuditReferenceLabels`, wrapping it as `{id, label}` (a `ReferenceValue`) in place of the bare id string, for every field whose target is a single-field-name entity: `accountId`/`fromAccountId`/`toAccountId`, `categoryId`, `institutionId`, `paymentMethodId`, `vehicleId`, `investmentCategoryId`, `investmentSubcategoryId`, `segmentId`, `productId`/`investmentProductId`, `templateId` (→ `RecurringTemplate.description`). Resolved at write time, in the same transaction as the change (ADR 0022), so a later rename or delete never changes what an old row shows. Deliberately **not** resolved - `budgetId`, `planId`, `holdingId`/`investmentHoldingId`, `recurringTemplateVersionId` - because the referenced aggregate has no single label field to show (those aggregates already pass `null` as their own `entityLabel`); these fields stay bare ids. `label` is `null` when the referenced row no longer exists at write time (shouldn't happen inside the same transaction) or for a pre-existing audit row written before this resolution landed.
- Recurring catch-up (F007) records one `GENERATED` entry per template per run (`changes`: `count`, `firstDate`, `lastDate`), origin `SYSTEM`; nothing is recorded when the run generates nothing.
- ArchUnit rule (in the existing architecture test class, ADR 0014): every public method of an application command service that is `@Transactional` and not read-only must be in a class that depends on `AuditLog`/`AuditRecorder`, minus an explicit allowlist that PR 2 empties.

### Persistence
- New migration (`V18` — check the highest existing `V` number first; F024 also adds one, so whichever lands second renumbers):
  - `audit_log`: `id uuid pk`, `occurred_at timestamptz not null`, `entity_type varchar(40) not null`, `entity_id uuid not null`, `entity_label varchar(200)`, `action varchar(20) not null`, `origin varchar(10) not null`, `changes jsonb not null default '{}'`, `request_id varchar(64)`. No foreign keys. `CHECK` on `origin IN ('USER','SYSTEM')`.
  - Indexes: `(occurred_at desc)` and `(entity_type, entity_id)`.
- `occurred_at` comes from the injected `Clock`, never SQL `now()`, so tests control it.
- `entity_label` is bounded like other free text: cap the value at `TextFieldConstraints.MAX_DESCRIPTION_LENGTH` when it is derived from a description, and add a matching `varchar` size.
- JPA entity + adapter implementing `AuditLog`; the adapter reads the request id from the MDC (the same key F016 sets). Extends `AuditableEntity` only if it fits an append-only row; otherwise omits the audit columns and says so in the entity's javadoc.

### API
- `GET /api/audit-log?from=&to=&entityType=&action=&origin=&entityId=&page=&size=` → `PagedModel` of `{id, occurredAt, entityType, entityId, entityLabel, action, origin, changes, requestId}`, ordered `occurred_at desc, id desc`. All filters optional.
- No write endpoints. Nothing deletes or edits rows.
- Regenerate `frontend/src/api/generated/schema.ts`; check the generated shape of `changes` (free-form object) and `Instant`.

## Frontend
- `src/api/auditLog/` (`auditLog.ts` + `auditLogQueries.ts`) + MSW handlers, following the per-area folder convention (frontend `CLAUDE.md`'s "Layout") every other area already uses rather than one flat file.
- New Activity page (`/activity`), nav entry: filter bar (date range, entity type, action, origin) using `ResponsiveFilterBar`; results paginated and grouped by day in the browser's local zone; each row shows time, action, entity type, label and origin, and expands to the from/to diff.
- Friendly field labels from a small per-entity map; unknown fields fall back to the raw field name. Money and dates use the existing formatters. A resolved reference field (`{id, label}`) renders as `"Label (id)"`; a `null` label (unresolved field, or a pre-existing audit row) falls back to the bare id - no frontend lookups needed, since the backend resolves at write time.
- Mobile: a hand-built card list (not `ResponsiveTable`), 44px+ touch targets on the expand toggle. `ResponsiveTable`'s own row-expansion is tied to its column model (hidden columns as label/value pairs); this page's expansion is a field-by-field before/after diff grouped under day headers, a shape that didn't fit that abstraction, so the table/card split is implemented directly (mirroring `ResponsiveTable`'s breakpoint/44px conventions, per ADR 0019, without reusing the component itself).
- Logging: never send `changes` or `entityLabel` to the frontend `logger`.

## Dependencies
F001, F004, F005, F006, F007, F008, F009, F013 (confirms the log is *not* exported), F016 (request id), F019 (query hooks), F021 (responsive primitives), F022. F024 must merge first (F023 already has): F024 adds fields to `Transaction`, and F025 branches off `develop` after it so `toAuditSnapshot()` covers them (its completeness test fails otherwise).
