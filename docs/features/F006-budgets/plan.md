# F006 — Action Plan

**Depends on**: F001, F002, F004.

## Backend
- [ ] Add `domain/budget/Budget.java` and `BudgetVersion.java` (expense-category-only invariant, one-version-per-month invariant).
- [ ] Add `BudgetJpaEntity`/`BudgetVersionJpaEntity` (extend `AuditableEntity`), repositories, adapters.
- [ ] Flyway migration `V6__budgets.sql` with unique constraints.
- [ ] Application services: create budget (+ first version), add/replace a version for a month, resolve effective cap for a given month.
- [ ] Budget-vs-actual report query joining resolved cap with F004's transaction sums per category/month.
- [ ] REST controller + DTOs.
- [ ] Tests: version resolution picks the latest version `<=` target month, same-month edit replaces rather than duplicates, income-category rejection.

## Frontend
- [ ] `src/api/budgets.ts`.
- [ ] `src/features/budgets` — budget settings list, add/edit-cap forms, budget-vs-actual view with month picker and over-cap indicator.

## Verification
- [ ] Set a cap, log expenses under it, confirm actual tracks correctly for the current month.
- [ ] Change the cap; confirm prior months still show the old cap and the new month shows the new one.
- [ ] Confirm exceeding the cap triggers the visual indicator.
