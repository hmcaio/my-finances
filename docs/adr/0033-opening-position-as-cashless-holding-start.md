# 0033. Record a holding's starting quantity and cost basis as a cashless opening position

Status: Accepted (amends [0012](0012-investments-as-accounts-and-transfers.md), [0024](0024-trade-confirmations-as-multi-line-transfers.md))
Date: 2026-10-29

## Context
ADR 0012 and ADR 0024 made a trade confirmation the only way a holding gets a quantity and a cost basis, and a trade confirmation always moves cash: its cash side must be a non-`INVESTMENT` account and the backend derives the settlement `amount` from the lines. ADR 0012 states the consequence ("unpaired historical backfill" is not supported, "every buy/sell needs a cash account to move against"), and F003 makes an account's opening balance and date immutable.

A user who already owns a portfolio, and does not want to transcribe years of notas de negociação, therefore has to bend their cash account to get correct cotas held and amount contributed. Throwaway-stack runs of the realistic cases (cash account opened 2026-10-20 at 15,000, then +5,000 salary and -300 groceries, real cash 19,700; two FIIs costing 11,000 in total):

- A consolidated opening BUY from the existing cash account leaves it at 8,700 instead of 19,700, because the opening balance cannot be raised to absorb the cost.
- An `INCOME` plug of 11,000 repairs the balance but shows 16,000 of 2026 ledger income against 5,000 real, and leaves net worth before the account's opening date without its cash.
- A new cash account with opening = real cash + cost, the opening BUY from it, and closing the old account gives correct numbers, at the price of split cash history, deactivated recurring templates, a closed duplicate row, and an opening balance (30,700) that is not the real balance on any day.

All three exist only because quantity and cost basis cannot be recorded without a cash movement. The cash movement was never the information the user wants to enter.

## Decision
- **New aggregate `InvestmentOpeningPosition`**, linked to an `InvestmentHolding`: `asOfDate` (today or earlier, no lower bound), `quantity` and `unitPrice` (positive, 8 decimals, the same scale as a trade line; `unitPrice` is the broker's average price, "preço médio"). It has no cash account, no `Transfer` and no `taxes`. At most one per holding (unique on the holding). Generic: any holding of any product, not only FIIs.
- **It counts like a BUY line dated `asOfDate`** in every figure derived from trade lines: `cotasHeld` and `amountContributed` (`FiiPortfolioQuery`), the monthly `units` and `contributed` series (`InvestmentValueSeriesQuery`; its cost lands in its as-of month, so the monthly figures keep summing to the running total), the needs-snapshot rule (`InvestmentSnapshotFreshnessQuery`), and the split adjustment (ADR 0025: `asOfDate` plays the role of the trade date, so a later split multiplies it and one on or before `asOfDate` does not).
- **The current value is not stored on the position.** It is an optional input that records the holding's snapshot dated `asOfDate` in the same transaction through `InvestmentSnapshotService.record`, replacing a same-day snapshot, exactly as a trade line's `resultingBalance` does. Skipping it leaves the holding flagged as needing a snapshot. Net worth is untouched: the investment side still comes only from snapshots.
- **Date order is enforced.** A trade or trade edit dated before its holding's opening position is rejected (`409`), and creating or moving a position to a date after the holding's earliest trade is rejected (`409`). Same day is allowed. Without this, entering a position and then earlier trades would double-count cotas.
- **Edit and hard delete are allowed while the holding is open**, audited, and deleting a position leaves snapshots alone (as deleting a trade does). A position counts as holding history, so a holding that has one cannot be hard-deleted.
- **Two ways in**, one transaction each: an optional `openingPosition` object on `POST /api/investment-products` (product + first holding + position + snapshot together, the one-step "add FII" flow), and `PUT`/`GET`/`DELETE /api/investment-holdings/{id}/opening-position` for an existing holding.
- **Rejected alternatives.**
  - A *cashless trade line*: it would relax `Transfer` and `TradeConfirmation`, whose invariants (a cash account, an `INVESTMENT` account, an `amount` and direction derived from the lines, net zero rejected) exist to keep a trade honest; an exception punched through them would weaken every trade.
  - *Fields on `InvestmentHolding`*: nullable columns that most holdings never use, and no natural home for its own audit entity type, report section and date-order rules.
  - An *editable opening balance* (reverses F003's immutability and rewrites every balance from the account's opening date), an *income plug*, and the *new-account hybrid*: they leave the cash account carrying a fiction the user did not choose.

## Consequences
- ADR 0012's "unpaired historical backfill is not supported" is amended: it stays true for *cash* (money still enters and leaves an `INVESTMENT` account only through a trade confirmation, which this ADR does not touch), but a holding's starting quantity and cost basis now have a cashless path. ADR 0024's "a trade confirmation is the source of a holding's quantity" gains this one second source. ADR 0012's "nothing is *computed* from quantity/price" is unchanged: an opening position is plain recorded data, and the only arithmetic is the same `quantity × unitPrice` a buy line already uses.
- ADR 0025's rule that every site summing a product's quantities must go through the shared split adjustment now covers opening positions too. F028 is amended so that whichever of F038 and F028 ships second wires the other; F038 does not depend on F028.
- Every read site of trade lines gains a second source: `FiiPortfolioQuery`, `InvestmentValueSeriesQuery`, `InvestmentSnapshotFreshnessQuery`, `RealHasHoldingHistoryChecker`, and the Investments report (a third CSV section, "OPENING POSITIONS").
- New audit entity type (`INVESTMENT_OPENING_POSITION`), a new migration (`investment_opening_positions`), new problem types in `PROBLEM_TYPE_MESSAGES`, and a position card on the product detail page plus an optional section in the create-product form (English and Brazilian Portuguese).
- `InvestmentValueSeriesQuery`'s `units` was `null` for a product with no trade lines; it is non-null once the product has an opening position.
- A user can now enter a starting position for a holding that also has earlier-dated trades only by moving the position's date to on or before the earliest trade, or by deleting the earlier trades. This is deliberate: the position is the start of the holding's tracked history.
- Changes PRD §3, §5.4, §5.5, §5.8, §5.13, §5.14, §6.6, §6.9 and §9, and adds §5.15. The F008 "no opening balance on `INVESTMENT` accounts" rationale is unchanged (an opening position adds nothing to the account's balance, so nothing double-counts the first snapshot).
