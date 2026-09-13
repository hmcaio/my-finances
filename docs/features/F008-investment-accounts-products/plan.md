# F008 — Action Plan

**Depends on**: F001.

## Backend
- [ ] Add `domain/investmentaccount/InvestmentAccount.java`, `domain/investmentcategory/InvestmentCategory.java`, `domain/investmentproduct/InvestmentProduct.java`.
- [ ] Define `HasInvestmentHistoryChecker` port (fulfilled by F009 once it exists; return `false` unconditionally until then, so delete works normally pre-F009).
- [ ] Add JPA entities (extend `AuditableEntity`), repositories, adapters for all three.
- [ ] Flyway migration `V8__investment_accounts_and_products.sql`.
- [ ] Application services: CRUD for categories; create/edit/close/delete (guarded by the history checker) for accounts and products.
- [ ] REST controllers + DTOs.
- [ ] Tests: delete blocked once history checker returns `true` (mockable ahead of F009), delete allowed at zero history, category delete blocked when referenced by a product.

## Frontend
- [ ] `src/api/investmentCategories.ts`, `src/api/investmentAccounts.ts`, `src/api/investmentProducts.ts`.
- [ ] `src/features/investmentCategories` — same list/add/rename/delete pattern as F002.
- [ ] `src/features/investmentAccounts` — list, create/edit, close.
- [ ] `src/features/investmentProducts` — list per account, create/edit, close, delete gated on `hasHistory`.

## Verification
- [ ] Create an investment account/product with zero history; delete succeeds.
- [ ] Once F009 lands and a product has a snapshot, confirm delete is rejected and close is offered instead.
