# F008 — Action Plan

**Depends on**: F001, F015.

## Backend
- [ ] Write tests first for the delete-safety rule: delete is blocked once the (mockable) history checker returns `true`, allowed at zero history.
- [ ] Add `domain/investmentaccount/InvestmentAccount.java`, `domain/investmentcategory/InvestmentCategory.java`, `domain/investmentproduct/InvestmentProduct.java`, and the `HasInvestmentHistoryChecker` port, implementing the above to make those tests pass (fulfilled by F009 once it exists; return `false` unconditionally until then).
- [ ] Add JPA entities (extend `AuditableEntity`), repositories, adapters for all three.
- [ ] Flyway migration `V8__investment_accounts_and_products.sql`.
- [ ] Write tests for category delete being blocked when referenced by a product, then implement application services: CRUD for categories; create/edit/close/delete (guarded by the history checker) for accounts and products.
- [ ] REST controllers + DTOs.

## Frontend
- [ ] `src/api/investmentCategories.ts`, `src/api/investmentAccounts.ts`, `src/api/investmentProducts.ts`.
- [ ] `src/features/investmentCategories` — same list/add/rename/delete pattern as F002.
- [ ] `src/features/investmentAccounts` — list, create/edit, close.
- [ ] `src/features/investmentProducts` — list per account, create/edit, close, delete gated on `hasHistory`.

## Verification
- [ ] Create an investment account/product with zero history; delete succeeds.
- [ ] Once F009 lands and a product has a snapshot, confirm delete is rejected and close is offered instead.
