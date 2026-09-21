# F008 — Action Plan

**Depends on**: F001, F003, F004, F007, F015, F017 (all built; F003/F004/F007 get touched). See [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md).

## Backend
- [x] Write tests first for `Account`: `INVESTMENT` requires null opening balance/date, every other type requires both; then add `AccountType.INVESTMENT` and the type-dependent invariant.
- [x] Write tests for `AccountService` (`INVESTMENT` create with/without opening values, close blocked while a product is open) and `AccountBalanceQuery` (`INVESTMENT` returns `0` until F009), then implement; add `InvestmentProductRepository.existsOpenByAccountId`.
- [x] Write tests for `TransactionService` and `RecurringTemplateService` rejecting an `INVESTMENT` account (409, `AccountTypeNotAllowedException` per package), then implement.
- [x] Write tests first for the product delete-safety rule: delete is blocked once the (mockable) `HasInvestmentHistoryChecker` returns `true`, allowed at zero history; add the port, returning `false` unconditionally until F009 fulfills it.
- [x] Add `domain/investmentcategory/InvestmentCategory.java`, `domain/investmentsubcategory/InvestmentSubcategory.java`, `domain/investmentproduct/InvestmentProduct.java` (name invariants, immutable sub-category parent, `close()`), with domain tests.
- [x] Write service tests, then implement: category CRUD (delete blocked by sub-categories or products), sub-category CRUD (per-parent name uniqueness, unknown parent 404, delete blocked by products), product create/edit/close/delete (account must be an open `INVESTMENT` account, name unique per account but reusable across accounts, sub-category must belong to the category, delete guarded by the history checker).
- [ ] JPA entities (extend `AuditableEntity`), repositories, adapters for all three; make `AccountJpaEntity`'s opening columns nullable.
- [ ] Flyway migration (`V13`+ — check the highest existing number first): `accounts` type `CHECK` + nullable opening columns + type-dependent `CHECK`, the three new tables with the composite FK, and the Brazilian seed. Migration tests: existing accounts unchanged; the `CHECK` accepts/rejects as specified; composite FK rejects a mismatched category/sub-category pair and accepts a null sub-category; seed has the expected categories and sub-categories with no duplicates.
- [ ] REST controllers + DTOs (`CreateAccountRequest` cross-field validation, nested `subcategories` in the categories response, `hasHistory` on the product detail); regenerate `frontend/src/api/generated/schema.ts`.
- [ ] Update backend `CLAUDE.md`: the seeded investment categories/sub-categories join the "seed rows collide with `UNIQUE`" note; and root `CLAUDE.md`: `InvestmentSubcategory` joins the flat-taxonomy `MAX_NAME_LENGTH` list.

## Frontend
- [ ] `src/api/investmentCategories.ts`, `src/api/investmentSubcategories.ts`, `src/api/investmentProducts.ts`, MSW handlers, and the `Account` type/handlers for nullable opening fields.
- [ ] `src/features/investmentCategories` — two-level list: add/rename/delete for categories and sub-categories, with `conflictMessage`s.
- [ ] Account form/list/detail (F003): `INVESTMENT` type hides opening balance and date; handle null opening fields; detail lists products for `INVESTMENT`.
- [ ] `src/features/investmentProducts` — list per account, create/edit with the dependent category/sub-category selects, close, delete gated on `hasHistory`.

## Verification
- [ ] Browse the seeded taxonomy in settings; add a sub-category; confirm the product form's sub-category list follows the chosen category and a category-only product (Crypto) saves.
- [ ] Create an `INVESTMENT` account (no opening fields asked) and a product; delete the product with zero history and confirm it succeeds; posting a transaction or creating a recurring template on the `INVESTMENT` account returns 409.
- [ ] Close the `INVESTMENT` account while a product is open: rejected; close the product, then the account: accepted.
- [ ] Once F009 lands and a product has a snapshot, confirm delete is rejected and close is offered instead.
