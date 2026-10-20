# 0023. Generalize ticker/segment on InvestmentProduct; version the FII allocation plan like a budget; link dividends to Transaction like fuel details

Status: Accepted
Date: 2026-10-07

## Context

The existing investment model (F008/F009/F022/F023) has no ticker field — `InvestmentProduct.name` is free text that happens to hold things like "PETR4" — and no sector-level classification (shoppings, logística, papel, ...): `InvestmentSubcategory` is one flat level, and "REITs (FIIs)" is itself already a sub-category, not a per-sector breakdown. A new FII-focused page needs both, plus a versioned target allocation (percent per ticker) and a way to record monthly dividends that can be grouped by ticker.

Two constraints from existing decisions shape the options:

- PRD §3/§9 lists "no *computed* investment math" / "no cost-basis tracking" as an explicit non-goal, driven by ADR 0012: investment *value* is always a manual snapshot, never derived from quantity × price. However, F009's monthly value series already computes `contributed` (net cash moved) and `units` (running quantity) per product, on read, from the same `Transfer` rows a trade already writes — these are sums of plain recorded fields, not a price-derived valuation. "Amount contributed" and "cotas held" for the FII page are the same class of computation, just exposed as a running total instead of a monthly series point.
- ADR 0021 already solved an analogous problem for fuel: a `Transaction` needs an optional structured extra (`FuelDetails`) gated by a dedicated category flag (`fuel_category`), independent of `built_in`.

## Decision

1. **`InvestmentProduct` gains optional `ticker` and `segmentId`, generalized rather than FII-scoped at the domain layer.** A dedicated field keeps `name` free to stay descriptive ("Kinea Renda Imobiliária") while `ticker` holds "KNRI11". Both fields are usable by any product (stock, ETF, FII), so a later feature covering other ticker-based assets reuses them without a migration — even though the new FII page is the only UI that manages them for v1, and the allocation plan (below) only accepts FII-subcategory products.
2. **New `InvestmentSegment` aggregate**, flat and user-editable, same shape as `InvestmentCategory` — not a third taxonomy tier under `InvestmentSubcategory`. A segment (what kind of real estate an FII holds) is orthogonal to the category/sub-category taxonomy (which asset class it is), not a refinement of it: "REITs (FIIs)" already is the sub-category level, and other sub-categories (Stocks, ETFs, Fixed Income instruments) have no meaningful segment at all. A separate, nullable FK keeps the existing taxonomy untouched.
3. **`AllocationPlan`/`AllocationPlanVersion`/`AllocationPlanEntry` are versioned exactly like `Budget`/`BudgetVersion` (ADR 0002)**: forward-only history keyed by `YearMonth`, with the one same-month-correction carve-out `BudgetVersion.updateCap` already allows. A plan entry targets one FII-subcategory `InvestmentProduct`; a version's entries must sum to exactly 100%. Reusing the budget-versioning shape (rather than a single mutable row) keeps "what was my target allocation 6 months ago" answerable, consistent with why `Budget`/`RecurringTemplate` are versioned at all (ADR 0002).
4. **A dividend is a `Transaction`, not a new aggregate**, carrying a nullable `investmentHoldingId`, present if and only if the transaction's category is a new dedicated `dividend_category` flag on `Category` — independent of `built_in` and of `fuel_category` (ADR 0021), same reasoning: `built_in` means something else (renamable, delete-blocked) and doesn't structurally guarantee the invariant, so the fuel precedent of a separate single-purpose flag is reused rather than overloading an existing one. Unlike the fuel category, the dividend category can't be renamed either (only `fuel_category` set that precedent for "neither renamable nor deletable" — reused here since the dividend history view's grouping depends on the category identity staying stable, same as `fuelCategory`'s own javadoc rationale).
5. **"Cotas held" and "amount contributed" per product are computed on read, never stored** — sums of `Transfer.quantity` and net `Transfer.amount` (buys minus sells) across the product's holdings, the same computed-not-stored pattern `LatestInvestmentSnapshotQuery`/`InvestmentValueSeriesQuery` already use for `needsSnapshot` and the monthly `contributed`/`units` fields. This is a running total of the same already-recorded data, not a new price-derived calculation, so it does not expand the PRD §3 non-goal (amended alongside this ADR to say so explicitly).
6. **Actual allocation keeps using latest `InvestmentSnapshot` market value** (unchanged valuation source — ADR 0012), scoped to FII-subcategory holdings and rolled up per product (across its holdings/accounts) or per segment. The allocation plan's entries are the only new state; nothing about how a value is measured changes.

## Consequences

- `InvestmentProduct`, `Category` and `Transaction` each gain one or two new optional fields rather than new tables wrapping them, consistent with how F009 (`InvestmentTradeDetails` on `Transfer`) and F024 (`FuelDetails` on `Transaction`) extended existing aggregates for a record-only, category/account-gated detail.
- No rebalance-suggestion math, no dividend yield/% (no per-cota price exists to derive it from), and no generalized multi-asset-class UI ship with this feature — the data model is reusable, the UI and the plan's membership validation are not (documented as PRD non-goals/future directions).
- A `RecurringTemplate` targeting the dividend category is not blocked the way ADR 0021 blocks the fuel category: a dividend's only extra field is a simple holding reference, not a value object with several interdependent required fields that have no templatable representation, so there's no structural invariant forcing a rejection the way `FuelDetails` does.
- PRD §3, §5.8, a new §5.13, §6.9, §6.13 and §9 are updated alongside this ADR.

See [F026's spec](../features/F026-fii-portfolio/spec.md) for the full implementation.
