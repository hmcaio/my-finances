# F006 — Budgets

## Summary
Versioned monthly caps per expense category, and budget-vs-actual reporting using each month's historically correct cap (PRD §5.6, §6.4).

## Scope
- `Budget` (one per category) + `BudgetVersion` (the versioned cap history).
- Budget-vs-actual calculation per month, per category.
- Out of scope: any notification/alerting beyond an in-app visual indicator (PRD §6.4 — no email/push, per PRD §9).

## Backend

### Domain
- `domain/budget/Budget.java`: id, `categoryId` (must reference an `EXPENSE`-type category — reject creation against an `INCOME` category).
- `domain/budget/BudgetVersion.java`: id, `budgetId`, `monthlyCap` (positive), `effectiveFrom` (a year-month, e.g. `YearMonth`).
- Editing a cap creates a new `BudgetVersion` with `effectiveFrom` set to the target month; it never mutates an existing version (PRD §5.6). The "current" cap for a `Budget` is the version with the latest `effectiveFrom` that is `<=` the month in question.
- Only one `BudgetVersion` per `(budgetId, effectiveFrom)` — editing the cap for a month that already has a version for that exact month replaces that version rather than creating a duplicate for the same month (this is the one mutation allowed: correcting a same-month entry, not rewriting history for an earlier month).

### Stopping a budget (issue #61)
- A budget can be **stopped** from a month via a **tombstone version**: `BudgetVersion.tombstone(...)` has a `null` `monthlyCap` and means "no budget from this month onward" (PRD §5.6). `BudgetVersion.create` still requires a positive cap; only the tombstone factory, `stop()` and persisted rows may have a `null` cap (`isTombstone()`).
- `BudgetService.stop(budgetId, effectiveFrom)` replaces the version of that exact month in place (like `setCap`), otherwise adds a tombstone; it is a no-op if the budget is already stopped as of that month. Earlier months are untouched, and `setCap` on a stopped budget resumes it (a normal new forward version, or an in-place replace of the tombstone's own month), so months between stop and resume stay unbudgeted.
- `resolveEffective` is unchanged (a tombstone is just the latest version at or before the month); callers check `isTombstone()`. `BudgetReportQuery` **omits the line** of a budget whose effective version for the month is a tombstone (a budget with no version yet still yields a line with `cap = null`).
- The budget row is kept: `POST /api/budgets` for a category that already has one (stopped or not) is still `409`, and deleting the category stays blocked while its budget row exists.

### Persistence
- `BudgetJpaEntity extends AuditableEntity`: table `budgets` (`id uuid pk`, `category_id uuid not null unique references categories`).
- `BudgetVersionJpaEntity extends AuditableEntity`: table `budget_versions` (`id uuid pk`, `budget_id uuid not null references budgets`, `monthly_cap numeric` — nullable since `V16`: `null` is a tombstone, and the CHECK is `monthly_cap IS NULL OR monthly_cap > 0`, a deliberate exception to "positivity backed at DTO + domain + DB" —, `effective_from date not null` — stored as the first day of the month, unique constraint on `(budget_id, effective_from)`).
- Migration `V8__budgets.sql`, not `V6` as originally planned above — F004 (transactions), which landed after this spec was written, already claimed both `V5` (its main table) and `V6` (its description/additional_notes follow-up), and F005 (transfers) then claimed `V7` — same renumbering story as F003's V3→V4 and F004/F005's own V4→V5/V5→V7 notes in their spec.md files.

### API
- `POST /api/budgets` — create for a category (first `BudgetVersion` included in the payload).
- `GET /api/budgets` — list, each with its current cap. `stopped` is `true` when the version effective in the current month is a tombstone (then `currentCap` is `null` and `currentCapEffectiveFrom` is the month it was stopped from); a budget stopped from a *future* month is still `stopped = false`.
- `POST /api/budgets/{id}/stop` — body: `{ effectiveFrom }` (`yyyy-MM`, like the other requests), stores a tombstone (or converts the same-month version), returns the budget (`404` for an unknown id, `400` without `effectiveFrom`). Resume with `PATCH .../cap`.
- `PATCH /api/budgets/{id}/cap` — body: `{ monthlyCap, effectiveFrom }`, creates (or replaces, if same month) a `BudgetVersion`.
- `GET /api/budgets/report?month=YYYY-MM` — per-category `{ categoryId, cap, actual }` for the given month, `cap` resolved from the version effective that month, `actual` summed from F004's transactions in that category/month across all accounts.

## Frontend
- Budget settings view: list of budgeted categories with current cap, add-budget form (pick an unbudgeted expense category + initial cap), edit-cap action (which month it takes effect from is implicit — "now"/current month forward, per PRD's "effective going forward only").
- Stop action on each active row (confirm dialog: past months keep their cap); a stopped row reads "Stopped" and offers "Resume budget", which is the normal cap edit. The add-budget picker still excludes a stopped category (it already has its budget row).
- Budget-vs-actual view: bars per category for the selected month (default current), using the historically correct cap; visual indicator (e.g. red bar/badge) when actual exceeds cap.
- Month picker to browse prior months' budget-vs-actual.

## Dependencies
F001, F002 (categories), F004 (transactions, for "actual").
