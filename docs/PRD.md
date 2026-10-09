# My Finances — PRD

## 1. Overview

A local, single-user web application for tracking daily transactions across named financial accounts, enforcing category budgets, and computing net worth (assets − liabilities + investments). Manual data entry only — no bank sync/aggregation integration in this version.

Each transaction belongs to a specific account (e.g. "Itaú Checking", "Nubank Credit Card"). Asset accounts (checking, savings, cash) hold money you have; credit card accounts track money you owe, which increases when you spend on them. Paying a credit card statement is a **transfer** between accounts, not a new expense — this is what keeps spend and debt correctly separated instead of double-counting. Investment accounts are accounts too: buying or selling an investment is a transfer between a cash account and an investment account, and the investment side is valued by manual snapshots.

Designed for solo use today, with a data model that can extend to household/multi-user later without a rewrite (not built now — noted as a future direction).

## 2. Goals

- Log income and expense transactions by category, each tied to a specific account.
- Enforce monthly per-category budget caps with visibility into actual vs. budget.
- Track net worth as (asset account balances − credit card liabilities) + investment balances.
- Move money between accounts via transfers (e.g. paying a credit card from checking) without it counting as spend.
- Reduce repetitive entry for recurring bills/income via versioned templates.
- Single dashboard surfacing spend, budget status, account balances, net worth trend, and upcoming recurring items.

## 3. Non-Goals

- No bank/card auto-sync — manual entry / CSV import only, and CSV import is a stretch goal, not required for v1.
- No multi-currency support — single currency only.
- No *computed* investment math: a buy/sell records its quantity, unit price and taxes as plain data (§5.5), but nothing price-derived is computed from them — no live or computed market prices, no automatic gain/loss calculation. Investment values are always manually entered as snapshots (§5.8). Running totals of plain recorded fields — cotas held and amount contributed per FII product (§5.13) — are sums of `quantity`/`amount`, the same computed-not-stored style as §5.8's existing monthly `units`/`contributed` value-series fields, not a price-derived valuation, so they stay outside this non-goal.
- No manual physical assets (real estate, vehicles) in net worth — the `Vehicle` entity introduced for fuel-transaction tracking (§5.11) is a plain label for grouping fuel purchases, not a valued asset counted in net worth.
- No user-editable fuel types — Etanol, Etanol Aditivado, Gasolina and Gasolina Aditivada are a fixed list (§5.11); adding others (e.g. electric) is a future direction (§9).
- No authentication — app is single-user and bound to localhost only, not exposed to LAN or internet.
- No automatic bank statement reconciliation/matching — account balances are whatever the logged transactions and transfers compute to; there is no import-and-match step.
- No interest/fee accrual modeling on credit card liabilities — the balance owed only reflects logged expenses and payment transfers, not statement interest or fees.
- No loan amortization schedules — only checking/savings/cash/credit-card/investment account types are modeled (see §5.4); a general "loan" account type is a future direction.
- No dividend yield or any price-derived return calculation for FIIs (§5.13) — no per-cota market price is tracked (§3), only each holding's latest snapshot total value.
- No rebalance-suggestion amounts (how much to buy/sell to hit the allocation plan's target) — the FII page (§6.13) only compares target vs. actual percentages.

## 4. Users

Single user (the app's owner). Household/multi-user support is a possible future direction; the schema should avoid decisions that would make that impossible later, but no multi-user UI or auth is built now.

## 5. Data Model

### 5.1 Category
- `id`
- `name`
- `type`: `INCOME` | `EXPENSE`
- `built_in` — true for exactly one row per type, "Other Expense" (`EXPENSE`) and "Other Income" (`INCOME`), the fallback for anything that fits nowhere else. Like the built-in institution (§5.10), it can be renamed but never deleted; identity is the flag, not the name. Set by the schema migration only; users can't create built-in rows.
- Flat list — no subcategories.
- Predefined starter set, fully editable (rename/add/remove) by the user, except that the two built-in rows can't be removed.

### 5.2 Payment Method
- `id`
- `name`
- Flat list — no subcategories.
- Predefined starter set (Debit Card, Credit Card, PIX, Cash), fully editable (rename/add/remove) by the user — same pattern as Category (§5.1).
- Orthogonal to Account (§5.4): a checking account can be moved via debit card or PIX; this field records which rail was used, the account records which pot of money it hit. Informational only — does not affect balance math.

### 5.3 Transaction
- `id`
- `date`
- `amount` (positive, sign implied by category type)
- `category_id`
- `type`: `INCOME` | `EXPENSE` (denormalized from category or derived)
- `account_id` — the account this transaction hits; drives that account's running balance (see §5.4)
- `payment_method_id` — informational only (see §5.2)
- `recurring_template_version_id` (nullable) — set when the transaction originated from a confirmed recurring occurrence
- `note` (optional free text)

An expense transaction on an asset account (checking/savings/cash) reduces that account's balance. An expense transaction on a credit card account **increases** the amount owed on that card.

### 5.4 Account
- `id`
- `name` (e.g. "Itaú Checking", "Nubank Credit Card")
- `institution_id` (required) — references an `Institution` (§5.10); "No institution" when the account isn't held at one
- `type`: `CHECKING` | `SAVINGS` | `CASH_WALLET` | `CREDIT_CARD` | `INVESTMENT`
- `opening_balance`: value as of `opening_balance_date`, set once when the account is created.
  - For `CHECKING` / `SAVINGS` / `CASH_WALLET`: an asset balance (money available).
  - For `CREDIT_CARD`: amount owed at the start (a liability).
  - For `INVESTMENT`: **must be empty** (null). An investment account's value comes only from its products' snapshots (§5.8), so an opening balance would be a field that never shows up in any balance — or, if it were added, would double-count the first snapshot.
- `opening_balance_date` (empty for `INVESTMENT`, same reason)
- `closed_date` (nullable) — set when the user closes the account.

An `INVESTMENT` account is a container for investment products (§5.8), e.g. "XP Investimentos" or "Previdência Privada". Transactions and recurring templates can't be posted to it — money enters and leaves it through transfers (§5.5), and its value is set by snapshots.

A closed account can no longer be selected for new transactions, transfers, or recurring templates — it drops out of the "create new" pickers and the live account balances widget (§6.8) — but all its historic rows are untouched, still counted correctly in past net worth (§5.9), and still included in data export (§6.9). Its balance is simply whatever it was as of `closed_date`; no further activity can touch it. Closing an account auto-deactivates (`active = false`) any `RecurringTemplate` (§5.7) still pointing at it, so no pending occurrence is ever generated into a closed account. An `INVESTMENT` account can only be closed once all its products are closed (§5.8).

An account is closed rather than deleted once it has any history, but one with none can be hard-deleted (ADR 0017): no transaction, transfer (either side), recurring template (active or not) or investment product (open or closed) references it. It works for open and closed accounts alike; otherwise the request is rejected and the user closes the account instead. An account with only an opening balance counts as history-free, yet it counts in past net worth (§5.9) from its opening date, so deleting it changes past net worth; the UI warns about this. Deleting the last account returns the app to onboarding (§6.7).

Running balance for an account at any point in time = `opening_balance` + sum(income transactions on it) − sum(expense transactions on it) + sum(transfers in) − sum(transfers out), all up to that date. For a `CREDIT_CARD` account, this running value **is** the amount currently owed — expenses increase it, transfers in (payments) decrease it. For an `INVESTMENT` account the balance is different: it is the sum of its products' latest snapshots as of that date (§5.8). Transfers in or out of it (buys and sells) do not change it — only snapshots do.

### 5.5 Transfer
- `id`
- `date`
- `from_account_id`
- `to_account_id`
- `amount` — the cash that actually moved
- `note` (optional)
- `investment_product_id` (nullable) — set when the transfer is a buy or sell of an investment product (§5.8)
- `quantity`, `unit_price`, `taxes` (all nullable) — trade details, only allowed together with `investment_product_id`

Represents money moving between two accounts you own — most commonly paying a credit card statement from a checking account. A transfer decreases the source account's balance and decreases the destination account's amount-owed (if a credit card) or increases its balance (if an asset account). Transfers are not categorized, do not count toward budgets, and do not change net worth (an asset going down and a liability going down by the same amount nets to zero) — they only move value between accounts.

**Buys and sells of investments are transfers**, not transactions: buying swaps cash for an investment and earns or spends nothing, so it must not count as an expense or income, hit a budget, or need a category or payment method (same reasoning as paying a card statement). A **buy** is a transfer whose destination is an `INVESTMENT` account, a **sell** one whose source is an `INVESTMENT` account; the direction is derived from the endpoints, not stored.
- A transfer touching an `INVESTMENT` account must name the `investment_product_id`, the product must belong to that account and be open, and exactly one endpoint may be an `INVESTMENT` account (moving between two investment accounts isn't modeled). Every buy or sell is paired with a tracked cash account; purchases funded from outside the app are not supported.
- The cash side changes immediately. The investment side changes only when a snapshot is recorded (§5.8), so net worth dips after a buy until the new snapshot is entered; the buy/sell form offers an optional resulting balance that records the snapshot in the same step.
- `quantity` and `unit_price` (up to 8 decimals, both positive, both or neither — some products such as fixed income or pension plans have no units) and `taxes` (brokerage, IOF, income tax withheld, ...; zero or more) are **record-only**. `amount` stays the source of truth for balances; the form prefills it as `quantity × unit_price + taxes` for a buy and `quantity × unit_price − taxes` for a sell, and the user can override it because brokers round differently. The backend does not enforce that equality.

### 5.6 Budget (versioned)
- `Budget`
  - `id`
  - `category_id` (expense categories only)
- `BudgetVersion`
  - `id`
  - `budget_id`
  - `monthly_cap` (amount; `null` on a **tombstone** version, see below)
  - `effective_from` (the first month this cap applies to)
  - Editing the cap creates a **new version**, effective going forward only; it does not alter prior versions.
  - **Stopping** a budget ("delete" without losing history) stores a **tombstone version**: a `BudgetVersion` with a `null` `monthly_cap`, meaning "no budget from `effective_from` onward". Earlier months keep their cap; months from the tombstone on have no budget (no line in the budget-vs-actual report); a later cap edit is a normal new version and **resumes** the budget, leaving the months in between unbudgeted. The budget row itself is never removed, so the category keeps its single budget (creating a second one is still rejected) and cannot be deleted while it exists. Rejected alternative: a nullable `ended_from` on `Budget`, which cannot represent stop-then-resume (the gap months would fall back to the old cap).
- Single period type for all categories: calendar month, resets monthly.
- "Actual" for a given month = sum of expense transactions in that category within the month, across all accounts.
- "Budget" for a given month = the `monthly_cap` of whichever `BudgetVersion` was effective during that month — so past months' budget-vs-actual stays accurate even after the cap is later changed.

### 5.7 Recurring Template (versioned)
- `RecurringTemplate`
  - `id`
  - `category_id`
  - `account_id` — the account the generated transaction will be posted to
  - `label` (e.g. "Rent")
  - `active` (boolean, default true) — when set to false, no further pending occurrences are generated; past transactions and version history are untouched. Reactivating (`active` back to true) resumes generation from the current version.
- `RecurringTemplateVersion`
  - `id`
  - `template_id`
  - `amount`
  - `day_of_month`
  - `effective_from` (date this version starts applying)
  - Editing a template's amount/day creates a **new version**; it does not alter prior versions.
- Generation is **lazy/catch-up**, not a real-time scheduler: on backend startup or next relevant request, for each active template, the system generates one pending occurrence for every cycle whose `day_of_month` has passed since the last occurrence was generated, each dated correctly for its own cycle. This matters because the app runs on-demand (§7.3) rather than staying always-on — if it's off for two months, coming back online produces two dated pending occurrences to confirm, not zero and not a merged one.
- The user **confirms** a pending occurrence, which creates a `Transaction` (on the template's account) linked to the specific `RecurringTemplateVersion` that generated it (`recurring_template_version_id`).
- Past transactions keep pointing to whatever version was active when they were created — editing the template later never rewrites history.
- A single past transaction can still be hand-edited directly (amount/date/account) without touching the version chain — this is a plain transaction edit, not a template edit.

### 5.8 Investment Category, Sub-category, Product, Holding & Snapshot (manual valuation)

Investments live in accounts of type `INVESTMENT` (§5.4) — there is no separate investment-account entity, so one account list, one institution link and one closing rule cover cash and investments alike. What is specific to investments is below; valuation stays manual (snapshots), and buys/sells are transfers (§5.5).

- `InvestmentCategory`: `id`, `name` — the top level of a two-level, user-editable taxonomy. Seeded with Brazilian defaults: Fixed Income, Variable Income, Funds, Pension (Previdência), International, Crypto, Other.
- `InvestmentSubcategory`: `id`, `investment_category_id` (required, fixed once created — re-parenting would silently reclassify every product below it), `name` — the instrument class within a category. Names are unique **per category**, not globally ("ETFs" and "Stocks" exist under both Variable Income and International). Seeded defaults:
  - Fixed Income: Tesouro Selic, Tesouro IPCA+, Tesouro Prefixado, CDB, LCI, LCA, CRI, CRA, Debentures, Savings (Poupança)
  - Variable Income: Stocks (Ações), REITs (FIIs), ETFs, BDRs
  - Funds: Fixed Income Funds, Multimercado, Equity Funds
  - Pension (Previdência): PGBL, VGBL
  - International: Stocks, ETFs, Bonds
  - Crypto and Other have no sub-categories.
- `InvestmentProduct`: `id`, `investment_category_id` (required), `investment_subcategory_id` (optional, and must belong to the chosen category — some categories, like Crypto, have no natural sub-level), `name` (the specific instrument, e.g. "PETR4", "BOVA11", "Tesouro Selic 2029"; unique across all products — one product represents that instrument everywhere it's held), `additional_notes` (optional) — pure taxonomy plus a free-text remark about the instrument itself; no account and no closed date (ADR 0020).
- `InvestmentHolding`: `id`, `product_id`, `account_id` (an `INVESTMENT` account, open), `closed_date` (nullable), `additional_notes` (optional) — the product as actually held in one account, unique per `(product_id, account_id)`. The same instrument held at two brokers ("Tesouro Selic 2029" at both XP and Nubank) is one `InvestmentProduct` with two holdings, not two products (ADR 0020). A holding is created explicitly ("add this product to this account"), never implicitly by recording a trade.
- `InvestmentSnapshot`: `id`, `holding_id`, `date`, `balance` (zero or more — `0` is a legitimate value for a liquidated position) — manual value entry per holding, added whenever the user wants to update it, no scheduled cadence. One snapshot per holding per date: entering another for the same date replaces it, which keeps "latest snapshot" unambiguous. A snapshot can also be edited (its date and balance) or deleted afterwards; moving it onto a date that already has one is refused, and so is any edit or delete that would leave a closed holding's latest snapshot non-zero.
- Latest snapshot per holding = current value of that holding. A product's total value (used in the category/sub-category allocation views) is the sum of its holdings' latest snapshots; an `INVESTMENT` account's balance is the sum of the latest snapshots of the holdings pointing at it (both rollups computed on read, not stored).
- **Keeping snapshots fresh.** Because a buy or sell (§5.5) changes cash immediately but a holding's value only when a snapshot is recorded, a holding whose latest transfer is dated after its latest snapshot (or that has a trade but no snapshot) is flagged as needing a snapshot, in the product list and on the allocation view. A trade can carry an optional resulting balance, which records that snapshot in the same step ("Sold entire position" records `0`). A holding can only be closed while its latest snapshot is `0` or absent, so a closed holding never keeps counting a stale value.
- Reclassifying a product changes how past dates group it in allocation views, since grouping uses its current category and sub-category.
- **Delete safety** — `INVESTMENT` accounts follow the `Account` rule (§5.4): closed once they have history, hard-deleted only while nothing references them. A holding with any snapshot or buy/sell transfer can't be hard-deleted either (it would orphan that history and break past net worth/allocation calculations) — it can only be closed (`closed_date` set), which drops it from "add a trade" pickers and live views while its history stays intact and exportable. Hard delete is only allowed for a holding with zero history. A product can only be hard-deleted while it has zero holdings at all. A category that still has sub-categories or products, or a sub-category that still has products, can't be deleted either.

### 5.9 Net Worth (computed, not stored)
`net_worth(as_of_date)` = Σ asset account balances (`CHECKING`/`SAVINGS`/`CASH_WALLET`, as_of_date) + Σ investment account balances (`INVESTMENT`, as_of_date; each the sum of the latest snapshots of the holdings it contains) − Σ credit card account balances (as_of_date)

An account counts at a given date only if it existed and wasn't yet closed then: opened on or before that date and either never closed or closed after it. This is what keeps past months unchanged when an account is closed later. An `INVESTMENT` account has no opening date; it simply contributes `0` until its first snapshot.

A time series for the net worth trend chart is computed either by evaluating this formula at each date where any underlying value changed (transaction, transfer, or investment snapshot), or at each month-end (the current month at today), carrying values forward through months with no activity. Both variants report the asset, liability and investment parts separately, so gross and net totals can both be charted.

### 5.10 Institution
- `id`
- `name` (e.g. "Nubank", "Itaú", "XP") — unique, flat and user-editable, same pattern as categories (§5.1). No type, no `closed_date`: it is a label, not something with a lifecycle.
- `built_in` — true for exactly one seeded row, "No institution", which stands in for money that isn't at any institution (cash wallet, a private pension). It can be renamed but never deleted, and it is the default when picking an institution. Set by the schema migration only; users can't create built-in rows.

An `Account` (§5.4, of any type including `INVESTMENT`) points at exactly one institution, so the same institution can group a checking account, a credit card and a brokerage account, and every account always has a value to group by. An institution that any account (open or closed) references can't be deleted — only renamed; re-pointing those accounts to another institution frees it. This is what makes "how much do I have at Nubank" answerable as a single grouping over §5.4 (see §9).

### 5.11 Vehicle & Fuel Details

**Vehicle**
- `id`
- `name` (e.g. "Civic", "Wife's Corolla") — unique, flat and user-editable, same pattern as categories (§5.1). A vehicle referenced by any fuel transaction (below) can't be deleted — only renamed.

**Fuel category**
- Exactly one `Category` (§5.1) carries `fuel_category` (true), a flag independent of `built_in`: a `Transaction`'s fuel details (below) may be present only when its category is this one, and must be present when it is. Like `built_in`, it can't be created by the user and is set once by the schema migration — but unlike `built_in`, this category can't be renamed either, since the flag's identity has to stay structurally trustworthy for the invariant it enforces ([ADR 0021](../adr/0021-fuel-details-on-transaction.md)).

**Fuel details** (optional, on `Transaction`, §5.3)
- `vehicle_id`
- `fuel_type`: `ETANOL` | `ETANOL_ADITIVADO` | `GASOLINA` | `GASOLINA_ADITIVADA` — fixed list, not user-editable (§3)
- `liters`
- `price_per_liter`
- `km_since_last_fill` (optional — absent for a vehicle's first recorded fill, since there's nothing to diff against)
- `odometer` (optional, purely informational — never used to derive `km_since_last_fill` or any ratio)

`amount` (§5.3) stays the cash that actually moved; it is not cross-validated against `liters × price_per_liter`, since receipts round each to different precision. Derived, computed on read and never stored: km per liter = `km_since_last_fill / liters`; amount per km = `amount / km_since_last_fill`; liters per km = `liters / km_since_last_fill`. All three are `null` when `km_since_last_fill` is absent.

### 5.12 Audit Log Entry
An append-only record of one committed change (ADR 0022). Not part of net worth or any report.
- `id`
- `occurred_at` (timestamp, from the application clock)
- `entity_type`, `entity_id` (no foreign key — a deleted entity's entries remain) and `entity_label` (a snapshot of the entity's name or description at the time, so a deleted entity is still readable)
- `action`: `CREATE`, `UPDATE`, `DELETE`, `CLOSE`, `REOPEN`, `STOPPED` (a budget stopped from a month on) or `GENERATED` (recurring catch-up summary)
- `origin`: `USER` or `SYSTEM` (lazy recurring generation and cascades such as closing an account deactivating its templates). There is no actor: the app has no authentication (§3).
- `changes`: field → `{from, to}` diff
- `request_id`: ties together the entries one click produced

Versioned entities (§5.6, §5.7) log as an `UPDATE` on the logical entity, diffed between the previous and the new version. An update that changes nothing is not logged. Entries are written in the same transaction as the change, are never edited or deleted, and are not part of the data export (§6.9).

### 5.13 Investment Segment, Allocation Plan & FII Dividends

**InvestmentSegment**
- `id`
- `name` (e.g. "Shoppings", "Logística", "Papel", "Lajes Corporativas") — flat, user-editable, same pattern as `InvestmentCategory` (§5.8), but orthogonal to the category/sub-category taxonomy: a sub-category like "REITs (FIIs)" is an asset class, a segment is what kind of real estate it holds. A segment referenced by any investment product can't be deleted — only renamed.

**InvestmentProduct gains** (§5.8) — generalized beyond FIIs, so a future ticker-based asset (stocks, ETFs) reuses the same fields without a migration, even though the FII page (§6.13) is the only UI that manages them for v1:
- `ticker` (optional, e.g. "KNRI11") — distinct from `name`, which stays the descriptive label (e.g. "Kinea Renda Imobiliária").
- `segment_id` (optional, references `InvestmentSegment`).

**AllocationPlan / AllocationPlanVersion** — a single, versioned target allocation across FIIs, same versioning pattern as `Budget`/`BudgetVersion` (§5.6):
- `AllocationPlan`: `id` — one implicit singleton for v1, not per-category.
- `AllocationPlanVersion`: `id`, `plan_id`, `effective_from` (`YearMonth`, like `BudgetVersion`). Editing the plan creates a new version effective from a month; it never mutates an earlier version — same forward-only history, and the same same-month-correction carve-out `BudgetVersion` has.
- `AllocationPlanEntry`: `version_id`, `investment_product_id` (must be classified under the "REITs (FIIs)" sub-category), `target_percentage`. All entries of one version must sum to exactly 100%.
- The plan version effective for a given month is resolved the same way as a budget's current cap (§5.6): the version with the latest `effective_from` that is `<=` that month.

**Dividend category**
- Exactly one `Category` (§5.1) carries `dividend_category` (true), a flag independent of `built_in` and of `fuel_category` (§5.11): a `Transaction`'s `investment_holding_id` (below) may be present only when its category is this one, and must be present when it is. Like the fuel category, it's seeded once by the schema migration and can't be created, deleted or renamed by the user.

**Dividend transaction link** (optional, on `Transaction`, §5.3)
- `investment_holding_id` — which holding paid the dividend. Present if and only if the transaction's category is the dividend category.

**Computed, not stored** (same sums-of-recorded-data style §5.8 already uses for `needsSnapshot` and the monthly value series — see §3):
- Cotas currently held, per product = sum of the `quantity` of every buy/sell transfer tagged with that product's holdings (buys positive, sells negative) — the running equivalent of §5.8's monthly `units`.
- Amount contributed, per product = sum of the `amount` of every buy transfer minus every sell transfer tagged with that product's holdings (net, not gross) — the running equivalent of §5.8's monthly `contributed`.
- Actual allocation percentage, per product or per segment = that grouping's total value (§5.8's "sum of latest snapshots") divided by the total value of all FII products, restricted to the "REITs (FIIs)" sub-category; by segment, a product with no `segment_id` groups under "No segment".
- Planned allocation percentage, per segment = the current `AllocationPlanVersion`'s entries' `target_percentage` summed by each entry's product's `segment_id` (same "No segment" grouping).

### 5.14 Investment Split

**InvestmentSplit** (corporate action: desdobramento/grupamento de cotas — a stock/FII split or reverse split)
- `id`
- `investment_product_id` (references `InvestmentProduct`, §5.8) — a split is an event on the security itself, so it's recorded once per product and applies to every holding/account that holds it.
- `effective_date` — today or earlier; not scheduled ahead of time, consistent with how the rest of the app records events after the fact rather than looking them up from a market-data source.
- `before_units` / `after_units` (positive integers, e.g. `1`/`10` for a 1:10 split, `10`/`1` for a 1:10 reverse split/grouping) — the ratio as the broker/exchange states it, not a pre-reduced decimal factor.
- `additional_notes` (optional, bounded free text, same convention as every other entity's optional notes field).
- Append-only: a product can have more than one split over its lifetime (separate corporate actions, possibly years apart). No edit — a mistaken entry is deleted and re-entered instead.
- Out of scope for v1: cash-in-lieu for a fractional remainder ("sobra de desdobramento" — when a ratio doesn't divide a holding's quantity evenly and the broker sells the leftover fraction for cash) — if that happens, the payout is recorded as an ordinary transfer, not by this feature (§9).

**Computed, not stored** (same sums-of-recorded-data style §5.8/§5.13 already use — see §3): cotas currently held (§5.13) and the monthly `units` value-series field (§5.8) both multiply each historical buy/sell transfer's recorded `quantity` by the product of every `InvestmentSplit`'s ratio (`after_units / before_units`) for that product whose `effective_date` falls after the transfer's date and on/before the point being computed (today for the running total, the series point's own month-end for each monthly point). A trade's own recorded `quantity`/`unit_price` is never rewritten — only the derived total changes, and it changes exactly at the split's effective month, which is a real jump in units held, not a smoothing artifact (the position's value is unaffected).

## 6. Functional Requirements

### 6.1 Transactions
- Create/edit/delete a transaction: date, amount, category, account, payment method, optional note.
- List/filter transactions by date range, category, account, and payment method.
- View running balance per account.

### 6.2 Accounts & Transfers
- CRUD on accounts (name, institution picked from §6.10 — defaults to "No institution", type, opening balance, opening balance date). For an `INVESTMENT` account the opening balance and date are not asked for (§5.4).
- View an account's running balance and its transaction/transfer history.
- Create a transfer between two accounts (date, from, to, amount, optional note); view transfer history. When one side is an `INVESTMENT` account the form also asks for the product and offers the trade details (quantity, unit price, taxes) and an optional resulting balance (§5.5, §6.6).
- Close an account (sets `closed_date`; auto-deactivates any recurring templates pointing at it). Closed accounts are excluded from "create new" pickers and the live balances widget, but remain browsable/exportable with their full history. An `INVESTMENT` account can only be closed once all its products are closed.
- Delete an account that has no history at all (§5.4, ADR 0017), open or closed; an account with any transaction, transfer, recurring template or investment product can only be closed. The confirmation warns that deleting an account with an opening balance changes past net worth.

### 6.3 Categories & Payment Methods
- CRUD on categories (name, type). Deleting a category with existing transactions should be blocked or require reassignment (implementation detail, decide at build time — flagged here as an edge case to resolve, not a new open decision for this PRD stage).
- CRUD on payment methods (name), same reassignment/blocking consideration on delete as categories.

### 6.4 Budgets
- Set a monthly cap per expense category.
- Edit a cap → creates a new version, effective going forward; prior months keep showing the cap that was actually in effect then.
- Stop budgeting a category from the current month (a tombstone version, §5.6): prior months keep their cap and the category leaves the report from that month on. Resume it later by setting a cap again.
- View budget-vs-actual for the current month (and prior months, using each month's effective cap) per category, across all accounts.
- Visual indicator when actual exceeds cap (in-app only — no notifications/email, consistent with no-auth/local-only scope).

### 6.5 Recurring Templates
- Create a recurring template (category, account, label, amount, day-of-month).
- Edit a template → creates a new version, effective going forward only.
- Stop a template: toggle `active` to false — no new pending occurrences; existing history preserved. Reactivate by toggling back to true.
- View/confirm pending occurrences (surfaced on the dashboard as "upcoming recurring bills").
- Confirming an occurrence creates a linked transaction; user may adjust the amount/date/account at confirmation time without creating a new template version (one-off variance on an otherwise-recurring bill).

### 6.6 Investments
- CRUD on investment categories and, under each, sub-categories (name), from a settings screen. A category with sub-categories or products, or a sub-category with products, can't be deleted; renaming is always allowed.
- Investment accounts are ordinary accounts of type `INVESTMENT` (§6.2). Create an investment product (name, category, optional sub-category, optional notes) together with its first holding (an `INVESTMENT` account); add further holdings for the same product in other accounts later. Edit a product's taxonomy/notes at any time. Close a holding instead of deleting it once it has any snapshot or buy/sell history — delete is only offered while it still has zero history; a holding can only be closed while its latest snapshot is `0` or absent. A product itself can only be hard-deleted while it has zero holdings.
- Buy or sell a product held in a given account: a shortcut that opens the transfer form (§6.2) with the product, quantity, unit price and taxes, a live total that prefills the amount, an optional resulting balance and a "Sold entire position" option that records a `0` snapshot. The account must already hold that product (a holding must exist first). Record-keeping only — quantity, price and taxes never affect computed value.
- Add a balance snapshot (date + balance) to any holding; a second one for the same date replaces the first. Edit a snapshot's date and balance or delete it from the holding's snapshot history (refused when it would clash with another snapshot's date, or leave a closed holding with a non-zero latest snapshot).
- View snapshot and buy/sell history per holding, plus a monthly value series per product (month-end value summed across its holdings from their latest snapshots, net contributions from buys minus sells, units held when quantities were recorded).
- List all investment products across accounts, filterable by category, sub-category, account (has a holding there) and status (open/closed, derived from whether any of its holdings is still open), searchable by name.
- View investment allocation: latest snapshot per holding, grouped and summed by category, by sub-category, or by account (pie chart; the category view drills into its sub-categories; products without a sub-category form a "No sub-category" slice). Holdings flagged as needing a snapshot (§5.8) are marked, rolled up to their product and account.
- Record a split or reverse split (desdobramento/grupamento) on a product: ratio (e.g. "1 : 10") and effective date, deletable; view a product's split history (§5.14). Changes only the derived cotas-held/units totals — past buy/sell records keep their originally recorded quantity and price unchanged.

### 6.7 Onboarding
- First-run flow: create at least one account, setting its opening balance and opening balance date (and its institution, which defaults to "No institution"; a new one can be created inline).

### 6.8 Dashboard
- Monthly spend by category (current month, chart or table).
- Budget-vs-actual bars, one per budgeted category.
- Account balances overview (list of accounts with current running balance).
- Net worth trend over time (line chart), switchable between change-date points and monthly points.
- Investment allocation by category, drilling into sub-categories (pie/bar chart, from latest product snapshots).
- Upcoming recurring bills (pending occurrences awaiting confirmation).

### 6.9 Data Export
- Exports **all** data, one CSV per entity, delivered as a single ZIP download: `categories.csv`, `payment_methods.csv`, `institutions.csv`, `accounts.csv`, `transactions.csv`, `transfers.csv`, `budgets.csv` (one row per `BudgetVersion`), `recurring_templates.csv` (one row per `RecurringTemplateVersion`), `investment_categories.csv`, `investment_subcategories.csv`, `investment_products.csv`, `investment_snapshots.csv`. Investment accounts are rows of `accounts.csv` (empty opening balance and date for `INVESTMENT`), and buys/sells are rows of `transfers.csv`, which also carries `investment_product_id`/`investment_product_name`, `quantity`, `unit_price` and `taxes` (empty for ordinary transfers).
- Every foreign key column is accompanied by the referenced name inline (e.g. a transaction row includes both `category_id` and `category_name`, both `account_id` and `account_name`) so each file is usable directly in a spreadsheet without joins, while still preserving ids for full-fidelity backup.
- Optional filters before export: date range, account, category. A filter only affects files with that dimension:
  - Date range: `transactions.csv`, `transfers.csv`, `investment_snapshots.csv`, `budgets.csv`/`recurring_templates.csv` (by each version's `effective_from`).
  - Account: `transactions.csv`, `transfers.csv` (matches either side), `recurring_templates.csv`.
  - Category: `transactions.csv`, `budgets.csv`, `recurring_templates.csv`.
  - Purely reference files with no date/account/category dimension of their own (`categories.csv`, `payment_methods.csv`, `institutions.csv`, `accounts.csv`, `investment_categories.csv`, `investment_subcategories.csv`, `investment_products.csv`) are always exported in full, since rows in the filtered files reference them by id and would be meaningless without them.
- No filter selected = full export of everything, unfiltered.
- `transactions.csv` also carries `vehicle_id`/`vehicle_name`, `fuel_type`, `liters`, `price_per_liter`, `km_since_last_fill` and `odometer` (§5.11), empty for non-fuel rows, plus `investment_holding_id` (§5.13), empty for non-dividend rows.
- `investment_products.csv` gains `ticker` and `segment_id`/`segment_name` (§5.13, empty when unset). Two new files: `investment_segments.csv` (always-full reference file, no date/account/category dimension) and `allocation_plan_entries.csv` (one row per entry, carrying its version's `effective_from` — narrowed by the date-range filter the same way `budgets.csv` is).

### 6.10 Institutions
- CRUD on institutions (name), from a settings screen next to categories and payment methods.
- An account (of any type, including `INVESTMENT`) always has an institution: it picks one from that list (defaulting to the seeded "No institution" row) and can change it; typing a new name in the picker creates the institution inline.
- "No institution" can be renamed but not deleted. Deleting any other institution referenced by an account (open or closed) is blocked; renaming is always allowed.

### 6.11 Fuel Tracking
- CRUD on vehicles (name), from a settings screen next to categories, payment methods and institutions. A vehicle referenced by any fuel transaction can't be deleted; renaming is always allowed.
- Recording a fuel purchase is an ordinary transaction (§6.1) with the fuel category (§5.11) selected: the form then also asks for vehicle, fuel type, liters, price per liter, an optional km since the last fill and an optional odometer reading. Changing the category away from the fuel category on an existing fuel transaction is rejected until its fuel details are cleared.
- Per-vehicle fuel history: a filtered transaction list for a selected vehicle, plus three time-series charts scoped to that vehicle — price per liter (one line per fuel type), km per liter, and amount spent per km — plotted per fill, with no date aggregation.

### 6.12 Activity Log
- A read-only Activity page lists every logged change, newest first, grouped by day in the viewer's local time zone, filterable by date range, entity type, action and origin. Each entry expands to its field-by-field before/after diff.
- Logged: every committed create, update, delete, close, reopen and stop on every entity; recurring catch-up as one summary entry per template per run (`SYSTEM`); side effects of a change as their own `SYSTEM` entries. Not logged: reads, navigation, rejected requests, updates that change nothing.
- The log starts when the feature is deployed; earlier history is not reconstructed. The log cannot be edited or cleared from the app, and there is no undo from it.

### 6.13 FII Portfolio
- A dedicated page, scoped to investment products classified under the "REITs (FIIs)" sub-category (§5.8).
- List every held FII: one row per product, aggregated across every account holding it (closed holdings hidden by default, with a filter to reveal them) — ticker, name, segment, cotas currently held, amount contributed, current value, latest snapshot date (§5.13).
- CRUD on investment segments (name), from a settings screen alongside investment categories (§6.6); assign a segment to a product from the product's existing edit form.
- Set/edit the allocation plan: target percentage per FII product, must sum to 100%, versioned by month like a budget (§6.4) — editing creates a new version effective going forward; correcting the current month's own version replaces it in place.
- Four pie charts: actual allocation by ticker, actual allocation by segment, planned allocation by ticker, planned allocation by segment (a product with no segment groups under "No segment" in both).
- Register a dividend via a dedicated form (pick the FII/holding, amount, date) that creates a transaction with the dividend category (§5.13) and the holding reference — distinct from the generic transaction form (§6.1).
- Dividend history: a list of every dividend transaction (date, ticker, amount), filterable by ticker and date range, with totals by ticker and by month/year. No yield or price-derived calculation (§3).

## 7. Technical Design

### 7.1 Stack
- **Backend**: Java, Spring Boot (REST API), built with Gradle.
- **Database migrations**: Flyway.
- **Frontend**: React + TypeScript, built with Vite, Material UI (MUI) for components/theming, React Router for client-side routing.
- **Database**: PostgreSQL, run via Docker (Docker Compose for local dev: db + backend, at minimum).
- **Auth**: none. App assumes a trusted single user on a trusted machine.
- **Network exposure**: backend/frontend bound to `localhost` only — not exposed to LAN or internet in this version.

### 7.2 Architecture & Methodology
- **Domain-Driven Design**: entity clusters in §5 map to aggregates/bounded contexts — e.g. Account + Transfer, Budget + BudgetVersion, RecurringTemplate + RecurringTemplateVersion, the Investment* cluster — each with its own consistency rules (e.g. a transfer's two-sided balance update, a budget version's forward-only effective date).
- **Hexagonal Architecture**: domain/application logic isolated from Spring Web/JPA/Postgres specifics behind ports, so the domain model and business rules (net worth calc, versioning, lazy recurring-occurrence catch-up) aren't coupled to framework or persistence details.
- **Test-Driven Design**: domain/business logic (net worth formula, versioning rules, recurring catch-up generation, budget-vs-actual by historical version) driven by tests written first, given how much of this system is rules-based rather than simple CRUD.

### 7.3 Deployment/Runtime
- Local development/usage only. No cloud hosting in scope.
- Postgres data persisted via a Docker volume so data survives container restarts.
- Run on-demand — brought up when the user wants to use it, brought down otherwise — rather than kept always-on. No background job depends on the app being continuously running; anything cycle-based (recurring templates, §5.7) is computed as catch-up on next startup/access instead.

## 8. Open Implementation Details (not blocking, decide during build)

These are low-level choices left to implementation rather than product decisions:
- Exact predefined starter category and payment method lists.
- Category/payment-method deletion/reassignment behavior when transactions reference it.
- Currency precision/rounding rules.
- ~~Exact CSV column ordering/naming and ZIP file naming convention for data export (§6.9).~~ Decided in F013's spec.

## 9. Future Directions (explicitly out of scope for v1)

- Household/multi-user support with shared or split categories.
- Bank/card auto-sync (Open Finance aggregator) or CSV statement import.
- Multi-currency support.
- Computed investment tracking (cost basis, value derived from quantity × price, gain/loss). Quantity, unit price and taxes are already recorded per buy/sell (§5.5) but nothing is calculated from them; this would build on that data and the manual snapshots.
- Monthly value series grouped by investment category or sub-category (today the series is per product).
- Manual physical assets (real estate, vehicles) in net worth.
- Allocation by institution: how much is held at each institution (account balances, `INVESTMENT` accounts included, grouped by `institution_id`; the built-in "No institution" row is just another slice; how credit card balances net against an institution is decided when it is built), as a dashboard pie chart. The data model (§5.10) already supports it as a single grouping over accounts; the query and widget are a later feature.
- Undo/revert from the audit log (§5.12), retention of old entries, and entity-scoped history links from account or transaction pages.
- Loan account type with amortization schedules (beyond the current checking/savings/cash/credit-card/investment types).
- Interest/fee accrual modeling on credit card liabilities.
- Manual bank statement reconciliation (mark an account balance as matched against a real statement as of a date).
- User-editable/custom fuel types (e.g. for electric vehicles) — the fuel type list is fixed for v1 (§5.11).
- Auto-computing `km_since_last_fill` from consecutive odometer readings, or validating `amount` against `liters × price_per_liter` — both are recorded independently and trusted as entered (§5.11).
- Generalizing the FII page (§6.13), the allocation plan and the ticker/segment fields (§5.13) to other ticker-based assets (stocks, ETFs) — the data model is deliberately generic, but the UI and the plan's product-membership validation are FII-only for v1.
- Cash-in-lieu handling for a fractional split remainder ("sobra de desdobramento", §5.14) — recorded as an ordinary transfer for now, not a dedicated field on `InvestmentSplit`.
- Remote/LAN access with authentication.
- Notifications (email/push) for budget overages or pending recurring bills.
