# F007 — Recurring Templates

## Summary
Versioned recurring bill/income templates with lazy/catch-up occurrence generation (not a real-time scheduler, since the app runs on-demand — PRD §7.3) and a confirm-to-transaction flow (PRD §5.7, §6.5). The most rules-heavy feature in the system — a strong candidate for the test-first approach called out in PRD §7.2.

## Scope
- `RecurringTemplate` (+ `active` flag) and `RecurringTemplateVersion` (amount, day-of-month, effective date).
- Lazy/catch-up pending-occurrence generation on backend startup/next relevant request.
- Confirm flow: turns a pending occurrence into a real `Transaction` (F004), with the option to adjust amount/date/account at confirmation time without creating a new template version.
- Stop/reactivate (`active` toggle).
- Consuming F003's "account closed" event/port to auto-deactivate templates pointing at a closed account.
- Extended by [F008](../F008-investment-accounts-products/spec.md) ([ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md)): a template can't target an `INVESTMENT` account (409, `AccountTypeNotAllowedException`, on create and edit); the account picker excludes them. Confirming an occurrence with an adjusted account goes through F004's `TransactionService`, which applies the same rule.
- Out of scope: any real-time/cron scheduling infrastructure — explicitly rejected by the PRD's on-demand runtime model.

## Backend

### Domain
- `domain/recurringtemplate/RecurringTemplate.java`: id, `categoryId`, `accountId`, `description` (mandatory, max 150 chars — bounded free-text field, same convention as F002/F003/F004: length check in the domain constructor/mutator, `@Size` on request DTOs, matching `varchar(150)` column), `active` (default `true`).
- `domain/recurringtemplate/RecurringTemplateVersion.java`: id, `templateId`, `amount`, `dayOfMonth` (1–31, see clamping note below), `effectiveFrom`.
- Editing amount/day-of-month creates a new version (`effectiveFrom` = the month the change takes effect), never mutates a prior version.
- `dayOfMonth` values 29–31 clamp to the last day of shorter months at generation time (implementation detail, not specified further by the PRD — document the chosen clamping rule here once decided, e.g. "31" on a 30-day month generates on day 30).
- `close()` on `RecurringTemplate` sets `active = false` — this is the same operation as the user manually stopping it (PRD §5.5/§6.4 "stop a template"); F003's account-closed event calls this, it doesn't need a separate code path.

### Pending occurrence generation (the core piece of logic)
- A `RecurringOccurrence` is **not** a persisted entity of its own type distinct from a `Transaction` — a "pending" occurrence exists only until confirmed, at which point it becomes a `Transaction` linked via `recurringTemplateVersionId` (F004). Track "last generated cycle" via a `last_generated_for` (year-month) column on `RecurringTemplate` — the simplest way to know how many cycles have elapsed without a separate table, and it's updated only when a pending occurrence for that cycle is actually created (not when confirmed — a pending occurrence is generated once and may sit unconfirmed for a while).
- A "pending occurrence" that hasn't been confirmed yet needs its own lightweight persisted record (since it must survive a restart and be listed on the dashboard, F012) — `PendingRecurringOccurrence`: id, `templateId`, `templateVersionId`, `dueDate`, created once per cycle by the catch-up job, deleted once confirmed (converted to a `Transaction`) or the template is deactivated.
- Catch-up algorithm, run at backend startup and before any request that reads recurring data (e.g. an interceptor/filter, or simply invoked by every relevant application service — implementation detail): for each `active` template, compute the current active `RecurringTemplateVersion` (latest with `effectiveFrom <= today`), and for every month between `last_generated_for` (exclusive) and the current month (inclusive) whose `dayOfMonth` has passed, create a `PendingRecurringOccurrence` dated for that cycle (using whichever version was effective for that specific past month, not necessarily the current version — mirrors the budget/version resolution pattern in F006) if one doesn't already exist for that cycle (atomically: `insertIfAbsent`, backed by the unique constraint, because two catch-up runs can overlap — the startup runner and a page load, two tabs), then advance `last_generated_for`.
- Confirming a `PendingRecurringOccurrence`: creates a `Transaction` (F004) with `recurringTemplateVersionId` set to the version used for that occurrence, using the occurrence's amount/date/account as defaults but allowing the user to override any of them at confirmation time (PRD §5.7/§6.5) — the override does not create a new `RecurringTemplateVersion`, it's a one-off variance on the resulting transaction only. Deletes the `PendingRecurringOccurrence` row.
- Rejecting/dismissing a pending occurrence without confirming it (not explicitly specified by the PRD, but a natural UI affordance) — treat as an open implementation detail: either allow dismiss-without-transaction (deletes the pending row, no transaction created) or require every occurrence to eventually be confirmed or the template stopped. Recommend allowing dismissal, since forcing a decision on every historical catch-up occurrence after a long time away would otherwise block the user.

### Persistence
- `RecurringTemplateJpaEntity extends AuditableEntity`: table `recurring_templates` (`id uuid pk`, `category_id uuid not null references categories`, `account_id uuid not null references accounts`, `description varchar(150) not null`, `active boolean not null default true`, `last_generated_for date`).
- `RecurringTemplateVersionJpaEntity extends AuditableEntity`: table `recurring_template_versions` (`id uuid pk`, `template_id uuid not null references recurring_templates`, `amount numeric not null`, `day_of_month int not null check (day_of_month between 1 and 31)`, `effective_from date not null`).
- `PendingRecurringOccurrenceJpaEntity extends AuditableEntity`: table `pending_recurring_occurrences` (`id uuid pk`, `template_id uuid not null references recurring_templates`, `template_version_id uuid not null references recurring_template_versions`, `due_date date not null`), with `UNIQUE (template_id, due_date)` (V11, issue #20 — V9 originally left it out and relied on the catch-up's own existence check, which two overlapping runs both pass; the catch-up now inserts with `ON CONFLICT DO NOTHING` via `PendingRecurringOccurrenceRepository.insertIfAbsent`, so a lost race is a no-op).
- Migration `V9__recurring_templates.sql` (not `V7` as originally planned here — F005 (transfers) claimed `V7` and F006 (budgets) claimed `V8` by the time this feature was built; same renumbering story as those two features' own spec.md notes). Also adds the FK from F004's `transactions.recurring_template_version_id` to `recurring_template_versions` (deferred there as a nullable column, constrained here once this table exists).

### API
- `POST /api/recurring-templates`, `GET /api/recurring-templates`, `PATCH /api/recurring-templates/{id}/cap` (amount/day, creates a version), `POST /api/recurring-templates/{id}/stop`, `POST /api/recurring-templates/{id}/reactivate`.
- `GET /api/recurring-templates/pending` — list of pending occurrences (dashboard-ready, F012), triggers catch-up generation first if not already run this request cycle.
- `POST /api/recurring-templates/pending/{id}/confirm` — body allows amount/date/account override; creates the transaction.
- `DELETE /api/recurring-templates/pending/{id}` — dismiss without confirming.

## Frontend
- Recurring templates settings view: list (description, category, account, amount, day-of-month, active/inactive), create/edit-cap forms, stop/reactivate toggle.
- "Upcoming recurring bills" widget (also embedded in F012's dashboard): pending occurrences with confirm (opens a pre-filled transaction form for override) and dismiss actions.

## Dependencies
F001, F002 (categories), F003 (accounts, and consumes its close event), F004 (transactions — confirming creates one).
