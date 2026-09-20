# F008 — Investment Accounts, Products & Taxonomy

## Summary
The `INVESTMENT` account type, `InvestmentProduct`, and the two-level investment taxonomy (`InvestmentCategory` → `InvestmentSubcategory`), with close-instead-of-delete once history exists (PRD §5.4 `INVESTMENT` type, §5.8 minus snapshots, §6.6 minus trades/snapshots/allocation, which are F009). Reworked by [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md): an investment account is an ordinary `Account` (F003) of a new type, not a separate entity, and buys/sells are transfers (F009).

## Scope
- `AccountType.INVESTMENT` on F003's `Account`: no opening balance or date, no transactions or recurring templates, closable only when all its products are closed.
- `InvestmentCategory` (top level) and `InvestmentSubcategory` (under a category), both flat and user-editable, seeded with Brazilian defaults.
- `InvestmentProduct`: name, parent `INVESTMENT` account, category, optional sub-category, closed date.
- Delete-safety rule for products: hard delete only allowed with zero history; otherwise close.
- Out of scope: snapshots, buy/sell transfers, the allocation view and the account's snapshot-based balance (F009) — they depend on this feature's entities existing first.

## Decisions
- **`INVESTMENT` is an `AccountType`, not a second account entity.** One account list, one institution link (F017), one closing rule. The investment-specific parts (products, taxonomy, snapshots) hang off `accounts`.
- **No opening balance for `INVESTMENT`.** Its value comes only from snapshots (F009); an opening balance would never appear in a balance, or would double-count the first snapshot. So `opening_balance` and `opening_balance_date` are null for this type and required for every other, enforced in the domain, the DTO and a DB `CHECK`.
- **"Category / sub-category", not "type / sub-type".** `type` already means a fixed enum in this codebase (`AccountType`, `CategoryType`, `transactions.type`). Two tables rather than a self-referencing tree, since the depth is fixed at two.
- **Sub-category is optional on a product.** Some categories have no natural sub-level (Crypto). A composite FK still guarantees that, when set, the sub-category belongs to the product's category.

## Backend

### Domain
- `domain/account/AccountType.java`: add `INVESTMENT`.
- `domain/account/Account.java`: `openingBalance` and `openingBalanceDate` are `null` iff `type == INVESTMENT`, and non-null otherwise (`create`/`reconstitute` re-check it; today both are unconditionally `requireNonNull`). Both stay immutable after creation, so a type can't be switched to or from `INVESTMENT` later. Their getters can now return `null`; every caller needs to cope (`AccountResponse`, the balance query, the export).
- `domain/investmentcategory/InvestmentCategory.java`: id, `name` (non-blank, capped at `TextFieldConstraints.MAX_NAME_LENGTH`, same convention as F002/F003) — same shape as F002's `Category` but a separate entity/table.
- `domain/investmentsubcategory/InvestmentSubcategory.java`: id, `investmentCategoryId` (required, no mutator — re-parenting would silently reclassify every product beneath it), `name` (same invariant). No import of `domain/investmentcategory`; the parent's existence is checked in the application service.
- `domain/investmentproduct/InvestmentProduct.java`: id, `accountId`, `investmentCategoryId`, `investmentSubcategoryId` (nullable), `name` (same invariant), `closedDate` (nullable). `close()` sets `closedDate` once.
- Uniqueness of names is an application-layer concern (`existsByName`/`existsByNameAndIdNot` on create/rename, 409, same pattern as F002/F003, see `V10__db_constraint_hardening.sql`): globally for categories and products, **per category** for sub-categories (`existsByInvestmentCategoryIdAndName` / `...AndIdNot`) — "ETFs" and "Stocks" legitimately exist under both Variable Income and International.
- Delete-safety invariant (PRD §5.8): a product can only be hard-deleted while it has zero history; once F009's snapshots or buy/sell transfers exist for it, only `close()` is permitted. This feature defines the port (`HasInvestmentHistoryChecker`) and returns `false` unconditionally until F009 implements it, so it doesn't depend on F009's tables. Accounts, including `INVESTMENT` ones, are never hard-deleted (F003 has no `DELETE`).

### Application
- `AccountService.create` for `INVESTMENT`: rejects an opening balance/date if given, and requires none. `AccountService.close` for an `INVESTMENT` account returns `409` while any of its products is open (`InvestmentAccountHasOpenProductsException`, via `InvestmentProductRepository.existsOpenByAccountId`).
- `AccountBalanceQuery.balanceAsOf` gets an `INVESTMENT` branch that returns `0` in this feature (no snapshots exist yet); F009 replaces it with the sum of the products' latest snapshots. Without the branch the null opening balance would throw.
- `TransactionService` and `RecurringTemplateService` reject an `INVESTMENT` account (`409`, `AccountTypeNotAllowedException` in each package beside the existing per-package `AccountClosedException`; validity depends on the account's persisted type). Money moves in and out of an `INVESTMENT` account through transfers only (F009).
- `InvestmentCategoryService`, `InvestmentSubcategoryService`, `InvestmentProductService`: plain use-case methods. Sub-category create verifies the parent exists (`InvestmentCategoryRepository.existsById`, else 404). Product create/edit verifies: the account exists (404), is an open `INVESTMENT` account (409, `InvestmentAccountRequiredException`), the category exists (404), and the sub-category, when given, belongs to that category (409, `InvestmentSubcategoryMismatchException`; the DB composite FK backs it).
- Delete guards (409): an `InvestmentSubcategory` referenced by any product (`InvestmentSubcategoryInUseException`); an `InvestmentCategory` referenced by any product or that still has sub-categories (`InvestmentCategoryInUseException`, same pattern as F002's `CategoryInUseException`). Renaming is always allowed.
- Reclassifying a product (PATCH is full-replace) is allowed. Allocation views group by the product's *current* classification, so this also regroups past dates; accepted.
- `close` on a product is `@Transactional`-free (one write). F009 adds the guard that a product can only be closed while its latest snapshot is `0` or absent.

### Persistence
Migration number TBD at implementation time (highest is `V12` today, F017's; every feature so far has had to renumber — check first). One migration:
1. `accounts`: replace the `type` `CHECK` with one that also allows `'INVESTMENT'` (V4 declared it inline and unnamed — look up the generated constraint name), drop `NOT NULL` on `opening_balance` and `opening_balance_date`, and add `CHECK ((type = 'INVESTMENT') = (opening_balance IS NULL AND opening_balance_date IS NULL))`. Existing rows stay valid (none is `INVESTMENT`, all have both values).
2. `investment_categories`: `id uuid pk`, `name varchar(100) not null unique`, plus audit columns. Seeded (below).
3. `investment_subcategories`: `id uuid pk`, `investment_category_id uuid not null references investment_categories`, `name varchar(100) not null`, `UNIQUE (investment_category_id, name)`, `UNIQUE (id, investment_category_id)` (target of the composite FK), plus audit columns. Seeded (below).
4. `investment_products`: `id uuid pk`, `account_id uuid not null references accounts` (indexed), `investment_category_id uuid not null references investment_categories`, `investment_subcategory_id uuid null`, `name varchar(100) not null unique`, `closed_date date`, plus audit columns, and `FOREIGN KEY (investment_subcategory_id, investment_category_id) REFERENCES investment_subcategories (id, investment_category_id)`. Postgres skips a composite FK when any of its columns is null, so a product without a sub-category is valid, while a sub-category from another category is rejected. Only an `INVESTMENT` account may own products — enforced in the service, since a cross-table `CHECK` isn't expressible.
- JPA entities extend `AuditableEntity` (Lombok as elsewhere in `infrastructure/persistence`), with package-private Spring Data repositories and adapters, one package per aggregate. `AccountJpaEntity`'s opening columns become nullable.
- All `name` columns are bounded (`varchar(100)`, `TextFieldConstraints.MAX_NAME_LENGTH`) and unique from the start — F002's `categories`/`payment_methods` originally shipped as unbounded `text` and needed `V3` and `V10` to fix.
- **Seed data** (Brazilian defaults, editable; English labels with Portuguese instrument names kept; `gen_random_uuid()` as in F002's `V2` for the one-time seed, sub-categories inserted with a subselect on the parent's name):
  - Fixed Income: Tesouro Selic, Tesouro IPCA+, Tesouro Prefixado, CDB, LCI, LCA, CRI, CRA, Debentures, Savings (Poupança)
  - Variable Income: Stocks (Ações), REITs (FIIs), ETFs, BDRs
  - Funds: Fixed Income Funds, Multimercado, Equity Funds
  - Pension (Previdência): PGBL, VGBL
  - International: Stocks, ETFs, Bonds
  - Crypto and Other, with no sub-categories.
- **Seed rows in real-DB tests** (same caveat as V2 and V12, backend CLAUDE.md): they exist in the shared Testcontainers Postgres before any test runs and rollback never removes them. Fixtures must not reuse these names (`" Test"` suffix) or assume the tables are empty.
- Test the `accounts` change against pre-existing rows (backend CLAUDE.md migration-test pattern: Flyway by hand into a throwaway schema, non-pooled `DriverManagerDataSource`): existing accounts keep their values, an `INVESTMENT` row without opening fields is accepted, an `INVESTMENT` row with them and a non-`INVESTMENT` row without them are rejected.

### API
- Accounts (F003's endpoints): `type` accepts `INVESTMENT`. `openingBalance`/`openingBalanceDate` become optional in `CreateAccountRequest`, with a cross-field validation (`400`): required unless `type == INVESTMENT`, and must be absent when it is. `AccountResponse` returns them as nullable. `balance` for an `INVESTMENT` account is `0` until F009. Regenerate `frontend/src/api/generated/schema.ts` (`npm run generate-api-types`, backend running).
- `GET /api/investment-categories` returns each category with its nested `subcategories: [{id, name}]` (one small call feeds the settings screen and every picker); `POST`/`PATCH` (`{name}`)/`DELETE /api/investment-categories[/{id}]` (`409` while it has sub-categories or products).
- `POST /api/investment-subcategories` (`{investmentCategoryId, name}`), `PATCH /api/investment-subcategories/{id}` (`{name}` only, full-replace), `DELETE /api/investment-subcategories/{id}` (`409` while referenced by a product).
- `POST/GET/PATCH /api/investment-products` (`GET` accepts `?accountId=`; bodies carry `accountId`, `investmentCategoryId` required, `investmentSubcategoryId` optional, `name`), `POST /api/investment-products/{id}/close`, `DELETE /api/investment-products/{id}` (only succeeds with zero history — `409` otherwise, directing the user to close instead). The detail response includes a `hasHistory` flag.
- Errors: nothing new in `GlobalExceptionHandler` — each new expected case has its own `@ResponseStatus` exception (backend CLAUDE.md).

## Frontend
- Settings "Investment categories": an expandable two-level list — add a category, add a sub-category under it, rename either, delete with explicit `conflictMessage`s for the two 409s (backend sends no text) — same list pattern as F002's categories.
- Account form (F003): add `INVESTMENT` to the type select; when chosen, hide the opening balance and date fields. Institution stays F017's `InstitutionSelect` (mandatory, defaults to "No institution"). Account list/detail handle a missing opening balance; an `INVESTMENT` account's detail page lists its products instead of transactions.
- Investment products list per account (name, category, sub-category), create/edit form with a category select and a dependent optional sub-category select filtered by the chosen category (reset when the category changes), close action; delete offered only while the product has no history (`hasHistory` from the detail response). Names of categories/sub-categories are joined client-side from the categories list.
- `src/api/investmentCategories.ts`, `investmentSubcategories.ts`, `investmentProducts.ts`, with matching MSW handlers.
- F011's onboarding is unchanged (it creates a cash account); F017's `InstitutionSelect` is reused as is.

## Dependencies
F001, F003 (extends `Account`), F004 and F007 (their services reject `INVESTMENT` accounts), F017 (institutions), F015 (frontend test tooling).
