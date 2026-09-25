# F009 — Action Plan

**Depends on**: F001, F003, F005, F008, F015. See [ADR 0012](../../adr/0012-investments-as-accounts-and-transfers.md).

## Backend
- [x] Write tests first for `InvestmentSnapshot` (balance `>= 0`, `replaceBalance`) and for `Transfer`'s trade details (details require a product, quantity and unit price both-or-neither, positivity, taxes `>= 0`), then implement `InvestmentSnapshot` and `InvestmentTradeDetails`.
- [x] Write tests for F008's `HasInvestmentHistoryChecker` implementation: `true` once a snapshot or a tagged transfer exists for the product; implement it.
- [x] Write tests for `LatestInvestmentSnapshotQuery` ("latest as of date" with several snapshots, none before the date), then implement it; switch `AccountBalanceQuery`'s `INVESTMENT` branch from `0` to the snapshot sum (extend `AccountBalanceQueryTest`).
- [x] Write tests for `InvestmentSnapshotService.record` (create, same-day replace, unknown product), then implement.
- [x] Write tests for `TransferService`'s investment rules — product required with an `INVESTMENT` endpoint, product must belong to the account, product without an `INVESTMENT` endpoint rejected, two `INVESTMENT` endpoints rejected, closed product rejected, on create and edit — then implement.
- [x] Write tests for `resultingBalance` (writes the transfer and the snapshot, replaces a same-day snapshot, `0` for a full sell) and a `TransferServiceTransactionalTest` proving a failed snapshot write rolls the transfer back (no class/method-level `@Transactional`, per backend CLAUDE.md), then implement the `@Transactional` create.
- [x] Write tests for `needsSnapshot` (trade after snapshot, trade with no snapshot, snapshot on/after the trade, as-of dates) and for the product close guard (`409` while the latest snapshot is non-zero; allowed at `0` or none), then implement both.
- [x] Write tests for the allocation grouping (`CATEGORY`, `SUBCATEGORY`, null sub-category slice, category total = sum of its sub-categories, zero/missing snapshots) and the value series (`null` before the first snapshot, contributions net of sells, units `null` without quantities), then implement them.
- [x] Add JPA entities/repositories/adapters (`InvestmentSnapshot`; extend `TransferJpaEntity` and its adapter) and the Flyway migration (`V15`, the next free number): `investment_snapshots` with `UNIQUE (product_id, date)`, and the `transfers` columns and `CHECK`s. Adapter/DB tests: `CHECK` violations, the unique constraint, existing transfers unaffected.
- [x] REST: snapshot endpoints, transfer DTOs (`CreateTransferRequest`/`UpdateTransferRequest`/`TransferResponse`, `investmentProductId` filter), product response flags, allocation and value-series endpoints; controller tests including an 8-decimal quantity/price round trip; regenerate `frontend/src/api/generated/schema.ts` and check the `YearMonth` shape.

## Frontend
- [x] `src/api/investmentSnapshots.ts`, `src/api/investmentAllocation.ts`, `src/api/investmentValueSeries.ts`; extend `src/api/transfers.ts` and the MSW handlers.
- [x] Extend the transfer form (F005) with the product select, quantity/unit price/taxes with live total, resulting balance and "Sold entire position".
- [x] Extend F008's product detail with snapshot form + history, trade history, Buy/Sell buttons, value-series chart, `needsSnapshot` badge, and the close-guard `conflictMessage`.
- [x] Allocation chart with category → sub-category drill-down and the stale footnote.

## Verification
- [x] Add a snapshot to a product; confirm F008's delete-safety now blocks hard-deleting that product, and a same-day second snapshot replaces the first. (Verified through the REST API against a running backend on a throwaway Postgres.)
- [x] Buy via transfer from checking with quantity, unit price and taxes: the total prefills `amount`, an override is kept, checking drops, and with a resulting balance the snapshot appears in the same step and net worth is unchanged; without one, the product shows `needsSnapshot` and net worth dips until a snapshot is added. (Not done in a browser: the API side - checking drops, the snapshot appears in the same step, `needsSnapshot` before/after, account balances - was verified against a running backend, and the form's prefill/override by component tests; net worth itself is F010, not built yet, so the account balances stand in for it.)
- [x] Sell with "Sold entire position": allocation and account balance drop at once and the product can then be closed; sell without a resulting balance and confirm the product can't be closed. (Verified through the REST API against a running backend.)
- [x] Add snapshots across several products/categories/sub-categories; confirm the allocation chart groups and sums correctly, drills into sub-categories, and totals match. (Not done in a browser: grouping and sums were verified through the REST API against a running backend, the drill-down by component tests.)
- [x] Confirm the value series shows month-end values, contributions and units for a product with a few buys and sells. (Verified through the REST API against a running backend.)

## Follow-up (issue #59)
- [x] Edit (date, balance) and delete snapshots: `InvestmentSnapshotRepository.findById`/`deleteById`, `InvestmentSnapshot.moveTo`, `InvestmentSnapshotService.update`/`delete` with the date-taken and closed-product `409`s, `PUT`/`DELETE .../snapshots/{snapshotId}`, inline edit and confirmed delete in the snapshot history with `conflictMessage`s, MSW handlers and regenerated API types. Service tests written first against the fakes.
