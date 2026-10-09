# 0025. Model a stock/FII split as a product-level event, applied as a read-side quantity adjustment

Status: Accepted
Date: 2026-10-09

## Context
ADR 0024 established that a product's currently-held quantity is never stored as a running balance — it's computed on read by summing every historical `TradeConfirmationLine`'s immutable `quantity`, signed by `side` (`FiiPortfolioQuery.cotasHeld`, and the monthly `units` field in `InvestmentValueSeriesQuery`). That sum implicitly assumes every trade line for a product is denominated in the same unit.

A split or reverse split (desdobramento/grupamento de cotas) breaks that assumption: the exchange/broker converts a holding's unit count on a given date with no money moving and no trade recorded (e.g. 5 units @ R$100 become 50 units @ R$10 on a 1:10 split). Without a model for this, trades recorded before the split date and trades recorded after it are in different units but get summed as if they weren't, silently undercounting (or overcounting, for a reverse split) every total computed from the split date onward — not a cosmetic display issue, a correctness bug that never self-corrects.

## Decision
- **New entity `InvestmentSplit`**: `productId`, `effectiveDate` (today or earlier — recorded after the fact, like every other event in this app; no market-data lookup), `beforeUnits`/`afterUnits` (two positive integers, the ratio as the broker states it, e.g. `1`/`10`), optional `additionalNotes`. Scoped to the product (`InvestmentProduct`), not to an individual holding — a split is a corporate action on the security itself and applies identically to every holder.
- **Append-only, no edit.** A product can carry more than one split over its lifetime. A mistaken entry is deleted and re-entered rather than edited — there's no downstream data that references a split row, so delete is cheap and sufficient.
- **Trade line data is never touched.** `TradeConfirmationLine`/`transfer_trade_lines` rows keep the exact `quantity`/`unitPrice` they were recorded with, forever — consistent with ADR 0024's "past facts stay as recorded." A split is deliberately not representable as a trade line: it moves no money and has no price, so it can't carry a `side`/`unitPrice` the way a buy/sell does.
- **The adjustment lives entirely on the read side.** Both existing computed-quantity sites — `FiiPortfolioQuery.cotasHeld` (current total) and `InvestmentValueSeriesQuery`'s monthly `units` point — multiply each trade line's quantity by the product of every `InvestmentSplit.ratio` (`afterUnits / beforeUnits`) for that product whose `effectiveDate` falls after the trade's date and on/before the point being computed (today for the running total; that month's point-date for a series point). Both sites call one shared helper rather than duplicating the multiplier logic.
- **No cash-in-lieu modeling.** A ratio that doesn't divide a holding's quantity evenly (a fractional remainder the broker settles in cash, "sobra de desdobramento") is out of scope — if it happens, the user records the payout as an ordinary transfer.
- Money fields are untouched: a split is value-neutral by definition, so `InvestmentSnapshot.balance` and the series' `value`/`contributed` fields need no adjustment.

## Consequences
- `cotasHeld` and the monthly `units` series will show a genuine step at a split's effective month (e.g. 5 → 50) — this is correct, not a smoothing artifact to avoid; the position's value is continuous across it, only the unit count changes, which is exactly what a split does.
- **Any future read site that sums a product's trade-line quantities must apply the same shared adjustment helper**, or its total will silently drift wrong from the first split onward — same class of risk as backend `CLAUDE.md`'s "a new table with an FK to `accounts` must be added to the usage checker" note; flag this in backend `CLAUDE.md` once built.
- Two separate features ship this: F028 (backend — `InvestmentSplit` domain/persistence/API and the adjustment helper wired into both queries) and F029 (frontend — split history + add/delete UI on the product detail page). Split at the user's request, unlike every prior investment feature which bundled both.
- Changes PRD §5.8/§5.13 (no cost-basis/price-derived math is still true — a split ratio is recorded data, not a computed valuation) by adding new §5.14 and a new bullet under §6.6.
