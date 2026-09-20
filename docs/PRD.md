# My Finances — PRD

## 1. Overview

A local, single-user web application for tracking daily transactions across named financial accounts, enforcing category budgets, and computing net worth (assets − liabilities + investments). Manual data entry only — no bank sync/aggregation integration in this version.

Each transaction belongs to a specific account (e.g. "Itaú Checking", "Nubank Credit Card"). Asset accounts (checking, savings, cash) hold money you have; credit card accounts track money you owe, which increases when you spend on them. Paying a credit card statement is a **transfer** between accounts, not a new expense — this is what keeps spend and debt correctly separated instead of double-counting.

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
- No ticker/share-count/cost-basis tracking, no live or computed prices, no automatic gain/loss calculation — investment values are always manually entered.
- No manual physical assets (real estate, vehicles) in net worth.
- No authentication — app is single-user and bound to localhost only, not exposed to LAN or internet.
- No automatic bank statement reconciliation/matching — account balances are whatever the logged transactions and transfers compute to; there is no import-and-match step.
- No interest/fee accrual modeling on credit card liabilities — the balance owed only reflects logged expenses and payment transfers, not statement interest or fees.
- No loan amortization schedules — only checking/savings/cash/credit-card account types are modeled (see §5.4); a general "loan" account type is a future direction.

## 4. Users

Single user (the app's owner). Household/multi-user support is a possible future direction; the schema should avoid decisions that would make that impossible later, but no multi-user UI or auth is built now.

## 5. Data Model

### 5.1 Category
- `id`
- `name`
- `type`: `INCOME` | `EXPENSE`
- Flat list — no subcategories.
- Predefined starter set, fully editable (rename/add/remove) by the user.

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
- `type`: `CHECKING` | `SAVINGS` | `CASH_WALLET` | `CREDIT_CARD`
- `opening_balance`: value as of `opening_balance_date`, set once when the account is created.
  - For `CHECKING` / `SAVINGS` / `CASH_WALLET`: an asset balance (money available).
  - For `CREDIT_CARD`: amount owed at the start (a liability).
- `opening_balance_date`
- `closed_date` (nullable) — set when the user closes the account.

A closed account can no longer be selected for new transactions, transfers, or recurring templates — it drops out of the "create new" pickers and the live account balances widget (§6.8) — but all its historic rows are untouched, still counted correctly in past net worth (§5.9), and still included in data export (§6.9). Its balance is simply whatever it was as of `closed_date`; no further activity can touch it. Closing an account auto-deactivates (`active = false`) any `RecurringTemplate` (§5.7) still pointing at it, so no pending occurrence is ever generated into a closed account.

Running balance for an account at any point in time = `opening_balance` + sum(income transactions on it) − sum(expense transactions on it) + sum(transfers in) − sum(transfers out), all up to that date. For a `CREDIT_CARD` account, this running value **is** the amount currently owed — expenses increase it, transfers in (payments) decrease it.

### 5.5 Transfer
- `id`
- `date`
- `from_account_id`
- `to_account_id`
- `amount`
- `note` (optional)

Represents money moving between two accounts you own — most commonly paying a credit card statement from a checking account. A transfer decreases the source account's balance and decreases the destination account's amount-owed (if a credit card) or increases its balance (if an asset account). Transfers are not categorized, do not count toward budgets, and do not change net worth (an asset going down and a liability going down by the same amount nets to zero) — they only move value between accounts.

### 5.6 Budget (versioned)
- `Budget`
  - `id`
  - `category_id` (expense categories only)
- `BudgetVersion`
  - `id`
  - `budget_id`
  - `monthly_cap` (amount)
  - `effective_from` (the first month this cap applies to)
  - Editing the cap creates a **new version**, effective going forward only; it does not alter prior versions.
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

### 5.8 Investment Account, Product & Snapshot (manual, no price/quantity math)

Distinct from the transactional `Account` in §5.4 — this is a separate container for investment holdings, still valued by manual snapshot rather than transactions.

- `InvestmentAccount`: `id`, `name` (e.g. "XP Renda Fixa", "Previdência Privada"), `institution_id` (required, references an `Institution`, §5.10), `closed_date` (nullable) — a container grouping investment products.
- `InvestmentCategory`: `id`, `name` (e.g. "Stocks", "Fixed Income", "ETF", "REITs (FIIs)", "Crypto") — flat, user-editable taxonomy, same pattern as transaction categories.
- `InvestmentProduct`: `id`, `account_id`, `category_id`, `name` (e.g. "PETR4", "BOVA11", "Tesouro Selic"), `closed_date` (nullable) — a specific holding within an investment account.
- `InvestmentBuySellLog`: `id`, `product_id`, `date`, `type` (`BUY` | `SELL`), `amount`, `note` (optional free text) — a record-keeping entry of when you bought/sold and how much; **not** used to compute current value (no quantity/price/cost-basis math).
- `InvestmentSnapshot`: `id`, `product_id`, `date`, `balance` — manual value entry per product, added whenever the user wants to update it, no scheduled cadence.
- Latest snapshot per product = current value of that product, used in net worth and in the category distribution view.
- An investment account's value = sum of its products' latest snapshots (display rollup only, not a separately stored value).
- Same delete-safety rule as `Account` (§5.4): an `InvestmentAccount` or `InvestmentProduct` with any snapshot or buy/sell history can't be hard-deleted (it would orphan that history and break past net worth/allocation calculations) — it can only be closed (`closed_date` set), which drops it from "create new" pickers and live views while its history stays intact and exportable. Hard delete is only allowed for one with zero history (nothing to lose).

### 5.9 Net Worth (computed, not stored)
`net_worth(as_of_date)` = Σ asset account balances (`CHECKING`/`SAVINGS`/`CASH_WALLET`, as_of_date) − Σ credit card account balances (as_of_date) + Σ latest snapshot per investment product (as_of_date)

A time series for the net worth trend chart is computed by evaluating this formula at each date where any underlying value changed (transaction, transfer, or investment snapshot).

### 5.10 Institution
- `id`
- `name` (e.g. "Nubank", "Itaú", "XP") — unique, flat and user-editable, same pattern as categories (§5.1). No type, no `closed_date`: it is a label, not something with a lifecycle.
- `built_in` — true for exactly one seeded row, "No institution", which stands in for money that isn't at any institution (cash wallet, a private pension). It can be renamed but never deleted, and it is the default when picking an institution. Set by the schema migration only; users can't create built-in rows.

An `Account` (§5.4) and an `InvestmentAccount` (§5.8) each point at exactly one institution, so the same institution can group a checking account, a credit card and a brokerage account, and every account always has a value to group by. An institution that any account or investment account (open or closed) references can't be deleted — only renamed; re-pointing those accounts to another institution frees it. This is what makes "how much do I have at Nubank" answerable as a grouping over §5.4/§5.8 (see §9).

## 6. Functional Requirements

### 6.1 Transactions
- Create/edit/delete a transaction: date, amount, category, account, payment method, optional note.
- List/filter transactions by date range, category, account, and payment method.
- View running balance per account.

### 6.2 Accounts & Transfers
- CRUD on accounts (name, institution picked from §6.10 — defaults to "No institution", type, opening balance, opening balance date).
- View an account's running balance and its transaction/transfer history.
- Create a transfer between two accounts (date, from, to, amount, optional note); view transfer history.
- Close an account (sets `closed_date`; auto-deactivates any recurring templates pointing at it). Closed accounts are excluded from "create new" pickers and the live balances widget, but remain browsable/exportable with their full history.

### 6.3 Categories & Payment Methods
- CRUD on categories (name, type). Deleting a category with existing transactions should be blocked or require reassignment (implementation detail, decide at build time — flagged here as an edge case to resolve, not a new open decision for this PRD stage).
- CRUD on payment methods (name), same reassignment/blocking consideration on delete as categories.

### 6.4 Budgets
- Set a monthly cap per expense category.
- Edit a cap → creates a new version, effective going forward; prior months keep showing the cap that was actually in effect then.
- View budget-vs-actual for the current month (and prior months, using each month's effective cap) per category, across all accounts.
- Visual indicator when actual exceeds cap (in-app only — no notifications/email, consistent with no-auth/local-only scope).

### 6.5 Recurring Templates
- Create a recurring template (category, account, label, amount, day-of-month).
- Edit a template → creates a new version, effective going forward only.
- Stop a template: toggle `active` to false — no new pending occurrences; existing history preserved. Reactivate by toggling back to true.
- View/confirm pending occurrences (surfaced on the dashboard as "upcoming recurring bills").
- Confirming an occurrence creates a linked transaction; user may adjust the amount/date/account at confirmation time without creating a new template version (one-off variance on an otherwise-recurring bill).

### 6.6 Investments
- CRUD on investment categories (name).
- Create/edit investment accounts (name, institution from §6.10 — defaults to "No institution") and investment products (name, account, category); close one instead of deleting it once it has any snapshot or buy/sell history (same rule as closing an `Account`, §6.2) — delete is only offered while it still has zero history.
- Log a buy/sell entry for a product (date, type, amount, optional note) — record-keeping only, does not affect computed value.
- Add a balance snapshot (date + balance) to any investment product.
- View snapshot/buy-sell history per product.
- View investment allocation by category: latest snapshot per product, grouped and summed by category (pie/bar chart).

### 6.7 Onboarding
- First-run flow: create at least one account, setting its opening balance and opening balance date (and its institution, which defaults to "No institution"; a new one can be created inline).

### 6.8 Dashboard
- Monthly spend by category (current month, chart or table).
- Budget-vs-actual bars, one per budgeted category.
- Account balances overview (list of accounts with current running balance).
- Net worth trend over time (line chart).
- Investment allocation by category (pie/bar chart, from latest product snapshots).
- Upcoming recurring bills (pending occurrences awaiting confirmation).

### 6.9 Data Export
- Exports **all** data, one CSV per entity, delivered as a single ZIP download: `categories.csv`, `payment_methods.csv`, `institutions.csv`, `accounts.csv`, `transactions.csv`, `transfers.csv`, `budgets.csv` (one row per `BudgetVersion`), `recurring_templates.csv` (one row per `RecurringTemplateVersion`), `investment_accounts.csv`, `investment_categories.csv`, `investment_products.csv`, `investment_buy_sell_log.csv`, `investment_snapshots.csv`.
- Every foreign key column is accompanied by the referenced name inline (e.g. a transaction row includes both `category_id` and `category_name`, both `account_id` and `account_name`) so each file is usable directly in a spreadsheet without joins, while still preserving ids for full-fidelity backup.
- Optional filters before export: date range, account, category. A filter only affects files with that dimension:
  - Date range: `transactions.csv`, `transfers.csv`, `investment_buy_sell_log.csv`, `investment_snapshots.csv`, `budgets.csv`/`recurring_templates.csv` (by each version's `effective_from`).
  - Account: `transactions.csv`, `transfers.csv` (matches either side), `recurring_templates.csv`.
  - Category: `transactions.csv`, `budgets.csv`, `recurring_templates.csv`.
  - Purely reference files with no date/account/category dimension of their own (`categories.csv`, `payment_methods.csv`, `institutions.csv`, `accounts.csv`, `investment_accounts.csv`, `investment_categories.csv`, `investment_products.csv`) are always exported in full, since rows in the filtered files reference them by id and would be meaningless without them.
- No filter selected = full export of everything, unfiltered.

### 6.10 Institutions
- CRUD on institutions (name), from a settings screen next to categories and payment methods.
- An account or investment account always has an institution: it picks one from that list (defaulting to the seeded "No institution" row) and can change it; typing a new name in the picker creates the institution inline.
- "No institution" can be renamed but not deleted. Deleting any other institution referenced by an account or investment account (open or closed) is blocked; renaming is always allowed.

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
- Exact CSV column ordering/naming and ZIP file naming convention for data export (§6.9).

## 9. Future Directions (explicitly out of scope for v1)

- Household/multi-user support with shared or split categories.
- Bank/card auto-sync (Open Finance aggregator) or CSV statement import.
- Multi-currency support.
- Quantity/price-based investment tracking (shares, cost basis, computed value, gain/loss) — a stricter version of the current buy/sell log + manual snapshot approach.
- Manual physical assets (real estate, vehicles) in net worth.
- Allocation by institution: how much is held at each institution (account balances plus latest investment snapshots, grouped by `institution_id`; the built-in "No institution" row is just another slice), as a dashboard pie chart. The data model (§5.10) already supports it; the query and widget are a later feature.
- Loan account type with amortization schedules (beyond the current checking/savings/cash/credit-card types).
- Interest/fee accrual modeling on credit card liabilities.
- Manual bank statement reconciliation (mark an account balance as matched against a real statement as of a date).
- Remote/LAN access with authentication.
- Notifications (email/push) for budget overages or pending recurring bills.
