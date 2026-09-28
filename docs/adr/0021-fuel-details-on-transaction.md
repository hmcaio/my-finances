# 0021. Record fuel purchases as Transaction + optional FuelDetails, gated by a dedicated category flag

Status: Accepted
Date: 2026-09-28

## Context
F024 adds car fuel-purchase tracking (PRD §5.11): date, description, fuel type, amount paid, liters, price per liter, odometer and km since the previous fill, per vehicle, plus derived km/L, spend/km and L/km ratios charted over time per vehicle.

A fuel purchase is real money leaving a real account — unlike an investment buy/sell (ADR 0012), which ADR 0001 already ruled out modeling as a Transaction because it isn't income/expense and shouldn't hit a budget or need a category. A fuel purchase is the opposite: it *is* an ordinary expense that should hit account balances, budgets, reports and export exactly like any other Transaction (§5.3) — the only question is where the extra fuel-specific fields live.

ADR 0012 already answered a structurally similar question for investment trades: extend the existing aggregate (`Transfer`) with an optional, record-only value object (`InvestmentTradeDetails`) rather than create a parallel entity, because a separate aggregate would have duplicated Transfer's endpoints, amount and history handling for a case that is otherwise a plain transfer. The same argument applies here, on `Transaction` instead of `Transfer`.

Fuel fields must appear together and only together — a transaction has fuel details when, and only when, it means to record a fuel purchase — but nothing on `Transaction` currently signals "this transaction is a fuel purchase" other than the category the user picks. `Category.built_in` (§5.1) exists but means something else ("system default, protected from deletion, freely renamable") for the two built-in rows, and a partial unique index caps `built_in` at one row per `CategoryType`; EXPENSE's slot is already taken by "Other Expense". Reusing it would either violate that constraint or conflate two unrelated concerns, and `built_in` categories can still be renamed, which isn't safe for a flag load-bearing enough to gate a value object's presence.

## Decision
- **`Transaction` gains an optional `FuelDetails` value object** (`vehicle_id`, `fuel_type`, `liters`, `price_per_liter`, `km_since_last_fill` nullable, `odometer` nullable), mirroring `InvestmentTradeDetails`: record-only, never feeding `amount` or balance math, no cross-validation between `amount`, `liters` and `price_per_liter`.
- **A new `Vehicle` aggregate** — flat taxonomy, name only, same CRUD/delete-protection pattern as Category/PaymentMethod/Institution.
- **`fuel_type` is a fixed enum** (Etanol, Etanol Aditivado, Gasolina, Gasolina Aditivada), not a user-editable taxonomy — unlike Category/PaymentMethod/Institution/Vehicle, there is no product need yet for custom fuel types, and adding one later is additive (widen the enum or promote it to a taxonomy table), not a breaking change.
- **A new `fuel_category` boolean on `Category`, independent of `built_in`.** Exactly one row can carry it (its own partial unique index, `Category` is otherwise unaffected), and unlike `built_in`, it blocks both delete *and* rename — the flag is structurally load-bearing for the invariant below, not just a UI-protection convenience like `built_in`.
- **Invariant, enforced at the application layer** (crosses into the Category aggregate, so it can't live in `Transaction`'s own constructor): a `Transaction` carries `FuelDetails` if and only if its category is the fuel category. Creating a fuel-category transaction without `FuelDetails`, or a non-fuel-category transaction with it, is rejected; editing an existing fuel transaction's category away from the fuel category is rejected until its `FuelDetails` is cleared first.
- **Ratios (km/L, spend/km, L/km) are computed on read**, never stored — each is a pure function of one transaction's own `amount`/`liters`/`km_since_last_fill`, consistent with every other derived value in this codebase (net worth, account balances, `needsSnapshot`).
- **Fuel fields are conditional additions to the existing Transaction form**, not a separate creation flow, so there remains exactly one way to create/edit a Transaction.

## Consequences
- Fuel spend is a full Transaction: it participates in account balances, budgets, reports and CSV export with no special-casing anywhere that already handles Transactions generically.
- `CategoryService` gains a second, independent delete/rename guard (`fuel_category`) alongside the existing `built_in` one — the two flags are deliberately not unified, so a future reader must not assume "protected category" means one thing.
- Two new precision exceptions to the root CLAUDE.md `numeric(19,2)` money rule, joining F009's `numeric(19,8)` on trade quantity/price: `liters`/`price_per_liter` as `numeric(19,3)` (pump/typical Brazilian fuel-price precision) and `km_since_last_fill`/`odometer` as `numeric(19,1)`.
- The three fuel charts (price/liter by type, km/L, spend/km) are always scoped to one selected vehicle — km-based ratios are meaningless mixed across vehicles, and price/liter follows the same scoping for consistency rather than special-casing one chart.
- A `Vehicle` referenced by any fuel transaction can't be hard-deleted, matching the existing Account/Category/PaymentMethod/Institution in-use pattern.
- Adds PRD §5.11 and §6.11, and a paragraph to §6.9 (export). Does not amend ADR 0001 or ADR 0012: this is a new instance of ADR 0012's "extend, don't duplicate" reasoning applied to `Transaction`, not a change to either prior decision.
