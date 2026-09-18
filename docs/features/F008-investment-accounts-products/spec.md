# F008 — Investment Accounts & Products

## Summary
`InvestmentAccount`, `InvestmentCategory`, `InvestmentProduct` CRUD, with close-instead-of-delete once history exists (PRD §5.8 entities, §6.6 minus buy/sell logs and snapshots, which are F009). Distinct from the transactional `Account` (F003) — a separate container for investment holdings.

## Scope
- `InvestmentAccount`: name, closed date.
- `InvestmentCategory`: flat, user-editable taxonomy (same pattern as F002's `Category`).
- `InvestmentProduct`: name, parent investment account, category, closed date.
- Delete-safety rule: hard delete only allowed with zero snapshot/buy-sell history; otherwise close.
- Out of scope: `InvestmentBuySellLog`, `InvestmentSnapshot`, and the allocation-by-category view (F009) — those depend on this feature's entities existing first.

## Backend

### Domain
- `domain/investmentaccount/InvestmentAccount.java`: id, `name` (non-blank, capped at `TextFieldConstraints.MAX_NAME_LENGTH`, same convention as F002/F003), `closedDate` (nullable).
- `domain/investmentcategory/InvestmentCategory.java`: id, `name` (same non-blank/length-capped invariant) — same shape as F002's `Category` but a separate entity/table (different taxonomy, not shared rows).
- `domain/investmentproduct/InvestmentProduct.java`: id, `investmentAccountId`, `investmentCategoryId`, `name` (same non-blank/length-capped invariant), `closedDate` (nullable).
- Uniqueness of `name` is an application-layer concern on all three (`existsByName`/`existsByNameAndIdNot` on create/rename, 409 — same pattern as F002/F003's post-F007 hardening, see CLAUDE.md), not a domain-constructor check.
- Delete-safety invariant (PRD §5.8): an `InvestmentAccount` or `InvestmentProduct` can only be hard-deleted while it has zero associated history; once F009's `InvestmentSnapshot`/`InvestmentBuySellLog` rows exist for it, only `close()` is permitted. This feature implements the `close()` behavior and the zero-history check as a port F009 fulfills (`HasInvestmentHistoryChecker` or similar), to avoid this feature depending on F009's tables directly.

### Persistence
- `InvestmentAccountJpaEntity extends AuditableEntity`: table `investment_accounts` (`id uuid pk`, `name varchar(100) not null unique`, `closed_date date`).
- `InvestmentCategoryJpaEntity extends AuditableEntity`: table `investment_categories` (`id uuid pk`, `name varchar(100) not null unique`).
- `InvestmentProductJpaEntity extends AuditableEntity`: table `investment_products` (`id uuid pk`, `investment_account_id uuid not null references investment_accounts`, `investment_category_id uuid not null references investment_categories`, `name varchar(100) not null unique`, `closed_date date`).
- All three `name` columns bounded `varchar(100)` (`TextFieldConstraints.MAX_NAME_LENGTH`) and `UNIQUE` from the start — F002's `categories`/`payment_methods` originally shipped as unbounded, unconstrained `text` and needed two follow-up migrations (`V3__bound_name_column_lengths.sql`, `V10__db_constraint_hardening.sql`) to fix; this feature should land with both already in place.
- Migration number TBD at implementation time (not necessarily `V8` — every feature since F004 has had to renumber its planned migration because an earlier-landing feature claimed the number first; check the highest existing `V*` migration before naming this one, per every prior feature's spec.md "not `VN` as originally planned" note).

### API
- `POST/GET/PATCH /api/investment-categories`, `DELETE /api/investment-categories/{id}` (blocked with `409` if referenced by a product, same pattern as F002).
- `POST/GET/PATCH /api/investment-accounts`, `POST /api/investment-accounts/{id}/close`, `DELETE /api/investment-accounts/{id}` (only succeeds with zero history — `409` otherwise, directing the user to close instead).
- `POST/GET/PATCH /api/investment-products`, `POST /api/investment-products/{id}/close`, `DELETE /api/investment-products/{id}` (same zero-history rule).

## Frontend
- Investment categories settings list (add/rename/delete) — same UI pattern as F002's categories.
- Investment accounts list (with closed toggle), create/edit form, close action.
- Investment products list per account (name, category), create/edit form, close action; delete offered only while the product has no history (frontend checks via the product's detail response, which includes a `hasHistory` flag).

## Dependencies
F001.
