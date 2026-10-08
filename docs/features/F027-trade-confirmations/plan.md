# F027 — Action Plan

**Depends on**: F005, F008, F009, F013, F015, F022, F026 (all built). See [ADR 0024](../../adr/0024-trade-confirmations-as-multi-line-transfers.md).

## Backend
- [ ] Write tests for `TradeSide` and `TradeConfirmationLine` (quantity/unitPrice mandatory and positive, resultingBalance optional and non-negative, `closeHolding` requires `side == SELL`), then implement.
- [ ] Write tests for `TradeConfirmation` (at least one line; at most one line per distinct product may carry a `resultingBalance`; `netCost(taxes)` sums BUY minus SELL plus taxes at full precision, rounds once HALF_UP to scale 2, and rejects an exact-zero result), then implement.
- [ ] Write tests for `Transfer.createTradeConfirmation`/`editTradeConfirmation` (derives `fromAccountId`/`toAccountId` from `netCost`'s sign, `amount` from its magnitude; existing plain `create`/`edit` behavior unchanged; existing `investmentProductId`/`tradeDetails`-based tests rewritten against the new shape), then update the domain class and remove `InvestmentTradeDetails`.
- [ ] Write tests for `TransferService`'s per-line holding validation (every line's `(productId, investmentAccountId)` needs an existing open holding, `404`/`409` as today but now checked per line; mixed BUY/SELL and duplicate-product lines accepted; per-line `resultingBalance` writes/replaces that holding's same-day snapshot; per-line `closeHolding` closes that holding; all in the same `@Transactional` use case), then implement.
- [ ] Write tests for the mutual-exclusivity rule at the application layer (`{fromAccountId,toAccountId,amount}` xor `{cashAccountId,investmentAccountId,tradeConfirmation}`, `400 InvestmentTransferInvalidException` otherwise), then implement.
- [ ] Write tests for `FiiPortfolioQuery`/`InvestmentValueSeriesQuery` against the new line-signed quantities (BUY adds, SELL subtracts, regardless of transfer direction; multiple lines for one product in one confirmation sum correctly), then update.
- [ ] Write tests for `InvestmentSnapshotFreshnessQuery.resolveHoldingId` reading the line's `productId`, then update.
- [ ] Write tests for `HasInvestmentHistoryChecker`/`HasHoldingHistoryChecker` against `transfer_trade_lines` instead of the flat column, then update.
- [ ] JPA entity for `TransferTradeLineJpaEntity` (child collection of `TransferJpaEntity`, cascade all + orphan removal, matching how `AllocationPlanEntry` hangs off `AllocationPlanVersion`, F026); `TransferTradeLineRepository`/adapter with `findByProductId`.
- [ ] Flyway migration (next free `V` number): create `transfer_trade_lines`; backfill one row per existing trade-tagged `transfers` row (side derived from existing direction, quantity/unit_price copied); drop `transfers.investment_product_id`/`quantity`/`unit_price` and their four related `CHECK` constraints; `taxes` stays. Migration test (throwaway schema + hand-run Flyway, backend CLAUDE.md pattern): a pre-existing trade ends up with one correctly-signed line row; a pre-existing plain transfer gets none.
- [ ] REST: `CreateTransferRequest`/`UpdateTransferRequest` gain `cashAccountId`/`investmentAccountId`/`tradeConfirmation`; `TransferResponse` gains `tradeConfirmation`; new `GET /api/trade-confirmation-lines?productId=` (`404` unknown product) backed by `TransferTradeLineRepository.findByProductId`; controller tests for the mutual-exclusivity 400, the per-line 404/409s, and the new endpoint. Regenerate `frontend/src/api/generated/schema.ts`.
- [ ] `DataExportService`: drop the four flat trade columns from `transfers.csv`; add `writeTransferTradeLines` → `transfer_trade_lines.csv` (`transfer_id`, `product_id`, `product_name`, `side`, `quantity`, `unit_price`, `resulting_balance`). Update `DataExportServiceTest`'s fixed trade-column assertions.
- [ ] Update root `CLAUDE.md`'s money-column exception list (quantity/unit_price precedent already documented under F009's exception — just note the table move) and backend `CLAUDE.md` if the per-line validation introduces a new logged event.

## Frontend
- [ ] `src/api/transfers/transfers.ts`: `Transfer` interface gains `tradeConfirmation` (lines array), drops `investmentProductId`/`quantity`/`unitPrice`/`taxes` as flat fields (`taxes` moves under `tradeConfirmation` in the request shape per spec, stays top-level in the response alongside it). Update `src/mocks/handlers/transfers.ts` fixtures and the `investmentProductId` filter to match against lines.
- [ ] New `src/api/investments/tradeConfirmationLines.ts` + `tradeConfirmationLinesQueries.ts` + MSW handler for `GET /api/trade-confirmation-lines?productId=`.
- [ ] `tradeMath.ts`: rework from a single quantity/unitPrice/taxes triple to per-line totals plus an overall net-settlement preview (mirrors backend `netCost`).
- [ ] `TransferForm.tsx`: redesign as a multi-line form — cash account + INVESTMENT account pickers, dynamic add/remove line rows (product scoped to the picked INVESTMENT account's holdings via `useInvestmentHoldingsByAccount`, side, quantity, unit price, optional resulting balance, "close this holding" checkbox shown only for SELL lines), one taxes field, and a read-only computed net-settlement/direction preview. Plain (non-trade) transfers keep today's single fromAccount/toAccount/amount form.
- [ ] `TransfersPage.tsx`: `tradeLabel` renders a chip per line, collapsing past a handful ("+N more").
- [ ] `InvestmentProductDetailPage.tsx`: trade table/card switches to `GET /api/trade-confirmation-lines?productId=` (one row per line), drops the Taxes column, each row links to its parent confirmation (`transferId`).
- [ ] Update every listed test (`TransfersPage.test.tsx`, `InvestmentProductDetailPage.test.tsx`, `transfers.test.ts`) for the new shape.

## Verification
- [ ] Record a confirmation with two products (one BUY, one SELL) and a single taxes figure: both holdings' trade history shows the right line; the transfer's direction/amount match the hand-computed net; CSV export shows one `transfers.csv` row and two `transfer_trade_lines.csv` rows.
- [ ] Record a confirmation with two lines for the same product (partial fills at different prices): both lines persist independently; `FiiPortfolioQuery`'s cotas-held sums both correctly.
- [ ] Record a confirmation whose net settlement is exactly zero: rejected with a clear error.
- [ ] Attempt `resultingBalance` on two lines sharing the same product in one confirmation: rejected.
- [ ] Attempt `closeHolding: true` on a BUY line: rejected.
- [ ] Record a single-line confirmation (today's simple buy/sell): behaves identically to before except `amount` is now backend-derived rather than user-typed.
- [ ] Edit an existing confirmation, changing a line's quantity: `amount`/direction recompute; other lines untouched.
- [ ] `InvestmentProductDetailPage`'s trade table for a product that appears in a multi-product confirmation: shows only that product's line(s), no Taxes column, and the row links through to the full confirmation.
- [ ] Migrate a database with pre-existing trade-tagged transfers: each ends up with exactly one correctly-signed `transfer_trade_lines` row; plain transfers are unaffected; existing reports (allocation, value series, FII portfolio) produce the same numbers as before the migration.
- [ ] Export a ZIP: `transfers.csv` has no product/quantity/price columns; `transfer_trade_lines.csv` is present and correct.
