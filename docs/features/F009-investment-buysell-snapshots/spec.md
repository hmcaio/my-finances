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
- `domain/investmentbuysell/InvestmentBuySellLog.java`: id, `productId`, `date`, `type` (`BUY`/`SELL`), `amount` (positive), `note` (nullable). No behavior beyond storage — explicitly not used to compute value (PRD §5.8).
- `domain/investmentsnapshot/InvestmentSnapshot.java`: id, `productId`, `date`, `balance`. No cadence/scheduling requirement — created whenever the user chooses.
- "Latest snapshot per product as of a date" is an application-layer query (`LatestInvestmentSnapshotQuery`), reused by this feature's allocation view and by F010's net worth calculation.

### Persistence
- `InvestmentBuySellLogJpaEntity extends AuditableEntity`: table `investment_buy_sell_logs` (`id uuid pk`, `product_id uuid not null references investment_products`, `date date not null`, `type text not null`, `amount numeric not null`, `note text`).
- `InvestmentSnapshotJpaEntity extends AuditableEntity`: table `investment_snapshots` (`id uuid pk`, `product_id uuid not null references investment_products`, `date date not null`, `balance numeric not null`).
- Migration `V9__investment_buysell_and_snapshots.sql`.
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
