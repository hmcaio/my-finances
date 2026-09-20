# 0012. Model investment accounts as accounts and buys/sells as transfers

Status: Accepted
Date: 2026-09-20

## Context
The first design of investments (PRD §5.8, F008/F009) was a parallel cluster: `InvestmentAccount` next to `Account`, and an `InvestmentBuySellLog` that only recorded that a buy or sell happened. Nothing was built yet, and two problems showed up on review:

- **Cash and investments were disconnected.** No way existed to move money from checking into an investment. Buying 1,000 of a product left checking unchanged and net worth jumped by 1,000 when the snapshot was entered, so the app overstated cash until the user compensated by hand with a made-up expense or transfer.
- **Two account containers.** `investment_accounts` duplicated `accounts` (name, institution, closed date, delete-safety rule, closing rule), and every feature that groups by institution had to union two tables.

Modeling a buy/sell as a `Transaction` was ruled out for the reasons ADR 0001 already settled for credit card payments: it would count as an expense or income, hit budgets, and require a category and payment method that mean nothing here. A `Transfer` already has the right shape — two endpoints, no category, outside budgets, neutral to net worth.

## Decision
- **Investment accounts are `Account`s** with a new type `INVESTMENT`. `investment_accounts` is dropped. `opening_balance`/`opening_balance_date` are null for this type (enforced in the domain, the DTO and a DB `CHECK`), because its value comes only from snapshots. Transactions and recurring templates cannot be posted to it. Its balance is the sum of its products' latest snapshots.
- **Buys and sells are `Transfer`s.** A transfer gains a nullable `investment_product_id`. A buy is a transfer into an `INVESTMENT` account, a sell one out of it; direction is derived from the endpoints. Every buy/sell pairs with a tracked cash account. `InvestmentBuySellLog` is dropped.
- **Trade details are record-only**: `quantity` and `unit_price` (8 decimals, both or neither) and `taxes`. `amount` remains the cash that moved and the only figure used in balances; the form prefills it from the details but the backend does not enforce the equality. This narrows the PRD non-goal "no quantity/price math" to "nothing is *computed* from them".
- **Snapshots stay the sole source of value**, one per product per day. Because a trade changes cash at once but value only on the next snapshot, a trade can carry an optional resulting balance that records the snapshot atomically, a product is flagged when a trade is newer than its latest snapshot, and a product can only be closed while its latest snapshot is `0` or absent.
- **Two-level investment taxonomy**: `InvestmentCategory` and `InvestmentSubcategory` (unique per parent) replace the flat, mixed-level list. Named "category"/"sub-category", not "type", because `type` already means a fixed enum throughout the codebase.
- **Net worth** sums account balances by type (assets and investments added, credit cards subtracted), and an account counts at date D only if it was opened on or before D and not closed by then. The old "every non-closed account" wording would have removed an account from all past months once closed.

## Consequences
- Cash and investments reconcile: checking drops on a buy, and net worth is corrected by the snapshot in the same step when the user gives a resulting balance. Until they do, net worth dips by the amount bought, and the product shows as needing a snapshot.
- One account list, one institution link and one closing rule. Allocation by institution becomes a single grouping over `accounts`.
- Balance math grows a third branch (`INVESTMENT` reads snapshots, not transactions/transfers), and `accounts.opening_balance`/`opening_balance_date` become nullable, guarded by the `CHECK` — `Account` getters for them can return null.
- Not supported: buys funded from outside the tracked accounts (salary deducted at the broker, an untracked bank) and unpaired historical backfill, since every buy/sell needs a cash account to move against. Taxes and fees sit inside the transfer `amount`, so they show up in net worth only through the lower snapshot, not as expenses in budgets or spend reports.
- Reclassifying a product regroups its past allocation, since allocation uses the product's current category and sub-category.
- Changes the F008/F009/F010/F013/F017 specs and PRD §5.4, §5.5, §5.8, §5.9, §6.2, §6.6, §6.9, §6.10. Amends ADR 0001 (its net worth formula, and the sentence extending the never-hard-delete rule to separate investment accounts); the rest of 0001 stands.
