# 0022. Record an audit log through an explicit application-layer port, in the same transaction as the change

Status: Accepted
Date: 2026-09-29

## Context
F025 adds a user actions log (PRD §5.12, §6.12): a read-only "Activity" feed answering "what changed, and when" across every aggregate. The app is single-user, local, has no authentication, and no domain-event infrastructure exists today (no `ApplicationEventPublisher`, no `@TransactionalEventListener`).

Three capture mechanisms were on the table: database triggers or JPA entity listeners (catch everything, know nothing about intent — they can't tell a user edit from a lazy recurring catch-up, can't group a cascade under one click, and hide behavior from the hexagonal layers, ADR 0004); domain events published by aggregates and persisted by a listener (clean, but builds event infrastructure for a single consumer); or an explicit output port called from application services.

Budgets and recurring templates are versioned (ADR 0002): a user's edit inserts a new version row rather than mutating one. A row-level trigger would log "version inserted", not "cap 500 → 600".

## Decision
- **An `AuditLog` output port in the application layer**, called explicitly from each write use case inside the same `@Transactional` as the business change. If the audit insert fails, the business write fails with it — no silently lost entries, no orphan entries for rolled-back changes. No domain events, no triggers, no aspect (an aspect is a possible later cleanup once the explicit calls prove uniform).
- **Each auditable aggregate exposes `toAuditSnapshot()`** (a flat field map). The service snapshots before and after, and a generic diff produces `{field: {from, to}}`. A test guards that every persisted field is either in the snapshot or on an explicit per-aggregate exclude list, so a field added later (e.g. F024's fuel columns on `Transaction`) can't be silently unaudited. An empty diff logs nothing.
- **One append-only `audit_log` table**, `jsonb` for the diff, no foreign key to the audited entity (delete events must outlive the row), an `entity_label` snapshot so a deleted entity is still readable in the feed, and the request id from the MDC so one click's entries group together.
- **`origin` is `USER` or `SYSTEM`**, not an actor: there is no authentication, so there is nobody to attribute to. Lazy recurring catch-up logs one `GENERATED` summary per template per run, `SYSTEM`; cascades (closing an account deactivating its templates) log their own `SYSTEM` entries under the same request id.
- **Versioned entities log as `UPDATE` on the logical entity**, with the diff taken between the previous and the new version; a budget tombstone logs as `STOPPED`.
- **Only committed successful changes are logged.** Rejected requests (409, validation) stay in the request-id logs (ADR 0011). Reads and navigation are never logged.
- **An ArchUnit rule (ADR 0014)** requires every write use case to depend on the `AuditLog` port, with an allowlist that shrinks to empty as the aggregates are instrumented.
- **Append-only, kept forever, no backfill, not exported.** The table lives in the same Postgres volume ADR 0015 already backs up, so a restore rolls the log back consistently with the data.

## Consequences
- Every new write use case must call the port; the ArchUnit rule turns a forgotten call into a failing test instead of a silent gap.
- Audit rows hold amounts, descriptions, notes and entity names in the database. That is deliberate and not a conflict with ADR 0011: the ban is on *log output*, so the audit feature must never echo `changes` or `entity_label` to a log line in either stack.
- Writes cost one extra insert in the same transaction; negligible at single-user volume.
- No undo. The from/to diffs make a revert feature possible later, but versioned entities and dependent rows need their own design.
- No retention: the table grows unbounded. A retention setting is the first follow-up if size ever matters.
- Adds PRD §5.12 and §6.12, one Flyway migration, and an `Upgrade:` note in the CHANGELOG. Does not amend any prior ADR.
