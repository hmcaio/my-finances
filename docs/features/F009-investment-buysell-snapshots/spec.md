# F009 — Investment Buy/Sell & Snapshots

## Summary
`InvestmentBuySellLog` (record-keeping only) and `InvestmentSnapshot` (the actual manually-entered value used everywhere else), plus the allocation-by-category view (PRD §5.8 logs/snapshots, §6.6 remaining). Fulfills F008's `HasInvestmentHistoryChecker` port.

## Scope
- `InvestmentBuySellLog`: date, type (BUY/SELL), amount, optional note — informational, never used in value calculations.
- `InvestmentSnapshot`: date, balance — the sole source of a product's current value.
- Allocation-by-category: latest snapshot per product, grouped by `InvestmentCategory`.
- Fulfilling F008's history-check port so delete-safety on investment accounts/products actually works once this feature exists.
- Out of scope: net worth itself (F010 consumes this feature's "latest snapshot per product" query).

## Backend

### Domain
- `domain/investmentbuysell/InvestmentBuySellLog.java`: id, `productId`, `date`, `type` (`BUY`/`SELL`), `amount` (positive - domain-constructor-checked, same `requireValidAmount` pattern as `Transaction`/`Transfer`/`BudgetVersion`/`RecurringTemplateVersion`), `note` (nullable). No behavior beyond storage — explicitly not used to compute value (PRD §5.8).
- `domain/investmentsnapshot/InvestmentSnapshot.java`: id, `productId`, `date`, `balance` (non-negative, domain-constructor-checked - `0` is a legitimate value for a liquidated-but-not-yet-closed position, so this is `>= 0`, not `> 0` like every other amount field in this codebase). No cadence/scheduling requirement — created whenever the user chooses.
- "Latest snapshot per product as of a date" is an application-layer query (`LatestInvestmentSnapshotQuery`), reused by this feature's allocation view and by F010's net worth calculation.

### Persistence
- `InvestmentBuySellLogJpaEntity extends AuditableEntity`: table `investment_buy_sell_logs` (`id uuid pk`, `product_id uuid not null references investment_products`, `date date not null`, `type text not null check (type in ('BUY', 'SELL'))`, `amount numeric(19,2) not null check (amount > 0)`, `note text`).
- `InvestmentSnapshotJpaEntity extends AuditableEntity`: table `investment_snapshots` (`id uuid pk`, `product_id uuid not null references investment_products`, `date date not null`, `balance numeric(19,2) not null check (balance >= 0)`).
- `amount`/`balance` are `numeric(19,2)`, matching the currency-minor-unit precision convention every other money column in this codebase uses (`transactions.amount`, `accounts.opening_balance`, etc.) - not plain unscaled `numeric`. Both `CHECK` constraints are defense in depth alongside the domain-constructor checks above and matching `@Positive`/`@DecimalMin(value = "0.0")` on the request DTOs - this codebase backs every cross-cutting amount invariant at all three layers (DTO, domain, DB) since the post-F007 schema audit found `amount > 0` was missing at the DB layer for F004-F007's own amount columns (see CLAUDE.md, `V10__db_constraint_hardening.sql`); land F009 with all three from the start rather than needing a follow-up migration. The `type` `CHECK` follows the same convention `categories.type`/`accounts.type`/`transactions.type` already use for every enum-backed `text` column.
- Migration number TBD at implementation time (not necessarily `V9` - see F008's spec.md note on the same issue; check the highest existing `V*` migration before naming this one).
- Implement `HasInvestmentHistoryChecker` (F008's port) here: `true` if any row exists in either table for the given product/account.

### API
- `POST /api/investment-products/{id}/buy-sell-logs`, `GET /api/investment-products/{id}/buy-sell-logs`.
- `POST /api/investment-products/{id}/snapshots`, `GET /api/investment-products/{id}/snapshots`.
- `GET /api/investments/allocation?asOf=` — `{ categoryId, categoryName, totalValue }[]`, computed from latest snapshot per product as of the given date (default today), grouped by category.

## Frontend
- Per-product detail view (under F008's investment product screens): buy/sell log entry form + history list, snapshot entry form + history list.
- Allocation-by-category chart (pie/bar) — reusable component also embedded in F012's dashboard.

## Dependencies
F001, F008 (investment accounts/products/categories).
