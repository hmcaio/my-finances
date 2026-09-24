# F009 — Investment Trades, Snapshots & Reports

## Summary
`InvestmentSnapshot` (the manually entered value used everywhere else), buys/sells as `Transfer`s tagged with a product (with quantity, unit price and taxes recorded as data), the snapshot-freshness rules that keep those two in step, the allocation view and a monthly per-product value series (PRD §5.5 trades, §5.8 snapshots, §6.6 remaining). Fulfills F008's `HasInvestmentHistoryChecker` port and gives `INVESTMENT` accounts their balance. Reworked by [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md): there is no separate buy/sell log.

## Scope
- `InvestmentSnapshot`: date, balance — the sole source of a product's current value; one per product per date.
- Buy/sell = an F005 `Transfer` between a cash account and an `INVESTMENT` account, tagged with `investmentProductId`, optionally carrying `quantity`, `unitPrice` and `taxes` (record-only).
- Snapshot freshness: an optional resulting balance on the trade, a `needsSnapshot` flag, and a close guard on products.
- `INVESTMENT` account balance = sum of its products' latest snapshots.
- Allocation by category or sub-category (latest snapshot per product, grouped).
- Monthly value series per product (value, contributions, units).
- Fulfilling F008's history-check port so delete-safety on products actually works once this feature exists.
- Out of scope: net worth itself (F010 consumes this feature's "latest snapshot per product" query); cost basis, gain/loss or any value derived from quantity/price (PRD non-goal); allocation by institution (later feature); grouping the value series by category.

## Decisions
- **A buy/sell is a transfer, not a transaction.** It swaps cash for an investment, so it must not count as expense/income, hit a budget or need a category and payment method (same reasoning as paying a credit card statement, ADR 0001). A transfer already has two endpoints and no category.
- **Snapshots stay the sole source of value; trades never feed balances of the investment side.** After a buy, cash drops at once but net worth dips by the amount until the next snapshot; after a sell, the old snapshot keeps counting until replaced. The freshness rules below exist to close that gap without computing values from quantity × price.
- **`amount` stays "cash that actually moved".** Buy: `quantity × unitPrice + taxes`; sell: `quantity × unitPrice − taxes`. The form prefills it and the user can override; the backend does not enforce the equality (brokers round per lot), so quantity/price/taxes never feed a balance.
- **Trade details are all optional and record-only.** Some products (fixed income, pension plans) have no units.
- **One snapshot per product per day.** Without it "latest snapshot" is ambiguous when a trade-time snapshot and a manual one share a date, and the monthly series would be nondeterministic.

## Backend

### Domain
- `domain/investmentsnapshot/InvestmentSnapshot.java`: id, `productId`, `date`, `balance` (non-negative, domain-constructor-checked — `0` is a legitimate value for a liquidated position, so this is `>= 0`, not `> 0` like every other amount field in this codebase). `replaceBalance(newBalance)` re-validates; no cadence/scheduling requirement.
- `domain/transfer/Transfer.java` (F005): gains nullable `investmentProductId` and an `InvestmentTradeDetails` value object (`quantity`, `unitPrice`, `taxes`, each nullable). Invariants: details require a product; `quantity` and `unitPrice` are both present or both absent, each `> 0`; `taxes`, when present, `>= 0`. The domain never imports the account or product packages.
- "Latest snapshot per product as of a date" is an application-layer query (`LatestInvestmentSnapshotQuery`), reused by the allocation view, `AccountBalanceQuery` and F010. A product with no snapshot on or before the date contributes nothing. Products need no closed-date filter: the close guard below means a closed product's latest snapshot is `0`.

### Application
- `TransferService` (F005) gains the investment rules; every case depends on persisted account/product state, so all are `409` with their own exception. Applied on create and edit:
  - an endpoint that is an `INVESTMENT` account requires `investmentProductId`, and the product must belong to that account;
  - `investmentProductId` requires exactly one `INVESTMENT` endpoint (a transfer between two `INVESTMENT` accounts isn't modeled);
  - the product must be open (`InvestmentProductClosedException`);
  - structural failures use `InvestmentTransferInvalidException`.
  Direction is derived: destination `INVESTMENT` = buy, source `INVESTMENT` = sell; it isn't stored. Quantity/price/taxes without a product, or a `resultingBalance` without one, are `400` (DTO).
- **Resulting balance.** `create` accepts an optional `resultingBalance` (`>= 0`). When present, the use case is `@Transactional` (multi-write, backend CLAUDE.md) and writes the transfer plus an `InvestmentSnapshot` dated the transfer date (a same-day snapshot is replaced). "Sold entire position" in the UI simply sends `0`. Editing a transfer never touches snapshots.
- **`InvestmentSnapshotService`**: `record(productId, date, balance)` upserts on `(productId, date)`; the product must exist. `findByProduct` (date descending).
- **Freshness flag.** A product `needsSnapshot` as of a date when it has a tagged transfer dated after its latest snapshot on or before that date, or a trade and no snapshot. Computed on read, never stored; returned on the product responses and on each allocation row.
- **Close guard.** `InvestmentProductService.close` (F008) returns `409` unless the latest snapshot is `0` or absent (`InvestmentProductNotEmptyException`), so a closed product never keeps counting a stale value.
- **`HasInvestmentHistoryChecker`** implementation: `true` if a snapshot row exists for the product or any transfer has `investment_product_id` = the product.
- **`AccountBalanceQuery`** (F003): the `INVESTMENT` branch F008 stubbed at `0` now sums the account's products' latest snapshots as of the date. Transactions and transfers on it don't contribute.
- **Allocation.** `groupBy=CATEGORY` groups the latest snapshots of all products by their category; `groupBy=SUBCATEGORY` by category then sub-category, with a null sub-category for products that have none. Products with a `0` or missing snapshot add nothing to a total, but a stale one still flags its group: a group whose only contributors are stale products with no value yet is emitted with a `0` total and `needsSnapshot: true`, so the freshness footnote is not lost (a group with no value and nothing stale is omitted). An `INVESTMENT` account counts at a date only if it existed then (its products' snapshots imply that).
- **Value series.** Per product and month-end (the current month at today): `value` (latest snapshot on or before the point, `null` before the first), `contributed` (that month's buys minus sells — cash moved, taxes included), `units` (running buys minus sells of `quantity`, `null` when the product has no recorded quantities). Raw data only.
- Logging (backend CLAUDE.md): ids and counts only, never amounts, quantities, prices or names; INFO for the snapshot written as part of a trade.
- **Product move guard.** `InvestmentProductService.edit` rejects moving a product to another account once it has history (`InvestmentProductMoveBlockedException`, `409`), because its past trades point at the old account and their derived direction and the value series' contributions would misread. Editing anything else stays allowed.

### Persistence
- `InvestmentSnapshotJpaEntity extends AuditableEntity`: table `investment_snapshots` (`id uuid pk`, `product_id uuid not null references investment_products`, `date date not null`, `balance numeric(19,2) not null check (balance >= 0)`, `UNIQUE (product_id, date)`).
- `transfers` (V7) gains: `investment_product_id uuid references investment_products` (indexed), `quantity numeric(19,8)`, `unit_price numeric(19,8)`, `taxes numeric(19,2)`. `CHECK (quantity IS NULL OR quantity > 0)`, `CHECK (unit_price IS NULL OR unit_price > 0)`, `CHECK (taxes IS NULL OR taxes >= 0)`, `CHECK ((quantity IS NULL) = (unit_price IS NULL))`, and `CHECK (investment_product_id IS NOT NULL OR (quantity IS NULL AND unit_price IS NULL AND taxes IS NULL))`. Existing rows satisfy all of these (new columns are null).
- The DTOs also bound the scale with `@Digits` (`quantity`/`unitPrice` 11 integer + 8 fraction digits, `taxes`/`resultingBalance`/`balance` 17 + 2), so an out-of-range number is a `400` rather than a database error.
- Scale 8 on `quantity`/`unit_price` is a deliberate exception to the root CLAUDE.md "money is `numeric(19,2)`" rule (fractional units, crypto, sub-cent prices); `taxes` and `balance` follow the rule.
- `balance`, `quantity`, `unit_price` and `taxes` are backed at all three layers (DTO `@PositiveOrZero`/`@Positive`, domain constructor, DB `CHECK`) from the start — the post-F007 audit found `amount > 0` missing at the DB layer for F004–F007 (see `V10__db_constraint_hardening.sql`).
- Migration: `V15__investment_snapshots_and_trades.sql` (`V13` is F008's, `V14` the built-in categories).

### API
- `POST /api/investment-products/{id}/snapshots` (`{date, balance}`; `201` when created, `200` when it replaced the same-day snapshot), `GET /api/investment-products/{id}/snapshots`.
- Transfers (F005's endpoints): create/update bodies gain optional `investmentProductId`, `quantity`, `unitPrice`, `taxes`; create also takes optional `resultingBalance`. Responses gain the four fields. `GET /api/transfers` gains an `investmentProductId` filter (the product's trade history).
- Investment products (F008): list/detail responses gain `needsSnapshot` (as of today) and `latestSnapshot` (`{date, balance}` of the most recent snapshot regardless of date, or null - the same snapshot the close guard looks at).
- `GET /api/investments/allocation?asOf=&groupBy=CATEGORY|SUBCATEGORY` (default `CATEGORY`, `asOf` defaults to today). `CATEGORY` rows: `{categoryId, categoryName, totalValue, needsSnapshot}`. `SUBCATEGORY` rows: `{categoryId, categoryName, subcategoryId, subcategoryName, totalValue, needsSnapshot}` with the sub-category fields null for products without one. Category totals equal the sum of their sub-category rows; `needsSnapshot` is true if any product in the group is stale. Both groupings return one row shape: with `CATEGORY` the sub-category fields are present and always null.
- `GET /api/investments/value-series?from=&to=&productId=` (`from`/`to` as `YearMonth`; `productId` omitted = one series per product) → `{productId, points: [{month, value, contributed, units}]}[]`. Check the generated schema for the `YearMonth` fields (root CLAUDE.md). `to` defaults to the current month and `from` to eleven months before it; months after the current one are not returned; a reversed range or one over 120 months is `400`; an unknown `productId` is `404`.

## Frontend
- Per-product detail view (under F008's product screens): snapshot entry form + history, the product's trade history (transfers filtered by product), Buy/Sell buttons, and a chart with the value line and contribution bars from the value series.
- **Transfer form** (F005) extended: when an `INVESTMENT` account is picked, show a product select (that account's open products), then quantity, unit price and taxes with a live total that prefills `amount` (buy: quantity × price + taxes; sell: quantity × price − taxes; the user can override), and an optional resulting balance. The resulting balance is prefilled with the prior latest snapshot plus the gross traded value (`amount − taxes` for a buy, `amount + taxes` for a sell, or quantity × price when given) as a suggestion the user edits (for a sell the gross traded value is subtracted from the prior snapshot, never below `0`). Clearing the field records no snapshot. A "Sold entire position" checkbox sets it to `0`. The Buy/Sell buttons open this form with the direction and product preset.
- `needsSnapshot` shows as a badge on the product list/detail and as a footnote on the allocation chart. Closing a product that still has value shows the `409` with an explicit `conflictMessage` pointing to "record a zero snapshot / sell entire position" (backend sends no text).
- Allocation chart component (`InvestmentAllocationChart`: a hand-drawn SVG donut with a legend, no chart dependency, prop-less like the other embeddable widgets) with click-to-drill from a category into its sub-categories, reusable and embedded in F012's dashboard. Until then it lives on the Investments page (`/investments`, replacing its placeholder), which also lists the investment accounts.
- `src/api/investmentSnapshots.ts`, `src/api/investmentAllocation.ts`, `src/api/investmentValueSeries.ts`; extend `src/api/transfers.ts` and its MSW handlers.
- The product detail view is the route `/investment-products/:id`, linked from the products list; Buy/Sell open the shared `TransferForm` in a dialog. A transfer's `409` now has several causes (closed account, the investment rules), so `createTransfer`/`editTransfer` share one `TRANSFER_CONFLICT_MESSAGE` naming them (it replaces `CLOSED_ACCOUNT_MESSAGE`).

## Dependencies
F001, F003 (`AccountBalanceQuery`), F005 (transfers), F008 (accounts/products/taxonomy), F015.
