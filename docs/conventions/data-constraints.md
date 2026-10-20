# Text-field limits and money precision: constants and exceptions

Root `CLAUDE.md` states the two rules (bounded free text at every layer, `numeric(19,2)` money with positivity at three layers). Read this when adding a text or amount field to find which constant or precision applies, or when a field looks like an exception.

## Free-text fields

All limits come from `domain/shared/TextFieldConstraints`; reuse its constants:

- `MAX_NAME_LENGTH` (100) for flat-taxonomy names: `Category`, `PaymentMethod`, `Account`, `Institution`, `InvestmentCategory`, `InvestmentSubcategory`, `InvestmentProduct`, `InvestmentSegment` and `Vehicle`. `InvestmentProduct.ticker` (F026) reuses it too: a flat-taxonomy-style name field, not a description.
- `MAX_DESCRIPTION_LENGTH` (150, mandatory) / `MAX_ADDITIONAL_NOTES_LENGTH` (500, optional) for the description/notes pair on `Transaction`, `Transfer` and `RecurringTemplate`.
- `MAX_SEARCH_TEXT_LENGTH` (100, F037) bounds the Ledger search query parameter (`@Size` on the `@RequestParam`, `maxLength` on the frontend input). It is the one limit with **no domain or column counterpart**, because the text is never stored.
- **Deliberate exception (F022)**: `InvestmentProduct.additionalNotes` and `InvestmentHolding.additionalNotes` are both standalone optional fields (`MAX_ADDITIONAL_NOTES_LENGTH`) with no paired mandatory description: `InvestmentProduct` is a taxonomy entity (like `Category`), and a holding has no "description" of its own to pair notes with.

## Money columns

`numeric(19,2)` (`transactions.amount`, `accounts.opening_balance`, `budget_versions.monthly_cap`, ...). Deliberate exceptions:

- `transfer_trade_lines.quantity`/`unit_price` (F009, moved by F027/ADR 0024 from a trade's flat `transfers.quantity`/`unit_price` to one row per product line) stay `numeric(19,8)` (fractional units, sub-cent prices); `transfers.taxes` stays on `transfers` itself at `(19,2)`: the confirmation's one aggregate figure, never apportioned per line.
- `investment_snapshots.balance` is `>= 0`, not `> 0`, because `0` is a liquidated position.
- `budget_versions.monthly_cap` is nullable (issue #61, `V16`) with `CHECK (monthly_cap IS NULL OR monthly_cap > 0)`, because `null` is a tombstone version meaning "budget stopped from this month": the DTOs and `BudgetVersion.create` still demand a positive cap, so a `BudgetVersion.getMonthlyCap()` reader must branch on `isTombstone()` (the budgets report omits such a budget for that month).
- (F024, ADR 0021) a fuel `Transaction`'s `liters`/`price_per_liter` are `numeric(19,3)` (pump/typical Brazilian fuel-price precision) and `km_since_last_fill`/`odometer` are `numeric(19,1)`, both nullable and bounded with `@Digits` on the request DTOs matching those scales.
- (F026, ADR 0023) `allocation_plan_entries.target_percentage` is `numeric(5,2)`: it isn't money, it's a percentage, bounded `0.01`-`999.99` by the column (generous beyond the valid `0`-`100` range; the DTOs enforce the real bound with `@DecimalMin`/`@DecimalMax`). The entries-sum-to-exactly-100 rule spans sibling rows, so it's an application-layer check, not a DB `CHECK`.
