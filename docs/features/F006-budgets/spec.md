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

### Persistence
- `BudgetJpaEntity extends AuditableEntity`: table `budgets` (`id uuid pk`, `category_id uuid not null unique references categories`).
- `BudgetVersionJpaEntity extends AuditableEntity`: table `budget_versions` (`id uuid pk`, `budget_id uuid not null references budgets`, `monthly_cap numeric not null`, `effective_from date not null` — stored as the first day of the month, unique constraint on `(budget_id, effective_from)`).
- Migration `V8__budgets.sql`, not `V6` as originally planned above — F004 (transactions), which landed after this spec was written, already claimed both `V5` (its main table) and `V6` (its description/additional_notes follow-up), and F005 (transfers) then claimed `V7` — same renumbering story as F003's V3→V4 and F004/F005's own V4→V5/V5→V7 notes in their spec.md files.

### API
- `POST /api/budgets` — create for a category (first `BudgetVersion` included in the payload).
- `GET /api/budgets` — list, each with its current cap.
- `PATCH /api/budgets/{id}/cap` — body: `{ monthlyCap, effectiveFrom }`, creates (or replaces, if same month) a `BudgetVersion`.
- `GET /api/budgets/report?month=YYYY-MM` — per-category `{ categoryId, cap, actual }` for the given month, `cap` resolved from the version effective that month, `actual` summed from F004's transactions in that category/month across all accounts.

## Frontend
- Budget settings view: list of budgeted categories with current cap, add-budget form (pick an unbudgeted expense category + initial cap), edit-cap action (which month it takes effect from is implicit — "now"/current month forward, per PRD's "effective going forward only").
- Budget-vs-actual view: bars per category for the selected month (default current), using the historically correct cap; visual indicator (e.g. red bar/badge) when actual exceeds cap.
- Month picker to browse prior months' budget-vs-actual.

## Dependencies
F001, F002 (categories), F004 (transactions, for "actual").
