# F006 — Action Plan

**Depends on**: F001, F002, F004, F015.

## Backend
- [x] Write tests first for the domain rules: a `Budget` can only target an `EXPENSE` category, version resolution picks the latest `BudgetVersion` with `effectiveFrom <= target month`, editing the cap for an already-versioned month replaces that version instead of duplicating it.
- [x] Add `domain/budget/Budget.java` and `BudgetVersion.java`, implementing the above to make those tests pass.
- [x] Add `BudgetJpaEntity`/`BudgetVersionJpaEntity` (extend `AuditableEntity`), repositories, adapters.
- [x] Flyway migration `V8__budgets.sql` (not `V6` — F004 claimed V5/V6 and F005 claimed V7 after this plan was written; see spec.md) with unique constraints.
- [x] Application services: create budget (+ first version), add/replace a version for a month, resolve effective cap for a given month.
- [x] Write tests for the budget-vs-actual report (correct cap resolved per month, actual summed across accounts), then implement the query joining resolved cap with F004's transaction sums per category/month.
- [x] REST controller + DTOs.

## Frontend
- [x] `src/api/budgets.ts`.
- [x] `src/features/budgets` — budget settings list, add/edit-cap forms, budget-vs-actual view with month picker and over-cap indicator.

## Verification
- [x] Set a cap, log expenses under it, confirm actual tracks correctly for the current month.
- [x] Change the cap; confirm prior months still show the old cap and the new month shows the new one.
- [x] Confirm exceeding the cap triggers the visual indicator.
