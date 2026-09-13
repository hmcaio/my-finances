# F009 — Action Plan

**Depends on**: F001, F008.

## Backend
- [ ] Write tests first for F008's `HasInvestmentHistoryChecker` implementation: returns `true` once either table has a row for the product.
- [ ] Add `domain/investmentbuysell/InvestmentBuySellLog.java`, `domain/investmentsnapshot/InvestmentSnapshot.java`, and implement the checker to make that test pass.
- [ ] Add JPA entities (extend `AuditableEntity`), repositories, adapters.
- [ ] Flyway migration `V9__investment_buysell_and_snapshots.sql`.
- [ ] Write tests for `LatestInvestmentSnapshotQuery` ("latest as of date" resolution when multiple snapshots exist), then implement it.
- [ ] Write tests for the allocation-by-category grouping, then implement the application service grouping `LatestInvestmentSnapshotQuery` results by `InvestmentCategory`.
- [ ] REST controllers + DTOs for logs, snapshots, and the allocation endpoint.

## Frontend
- [ ] `src/api/investmentBuySellLogs.ts`, `src/api/investmentSnapshots.ts`, `src/api/investmentAllocation.ts`.
- [ ] Extend F008's investment product detail view with buy/sell log and snapshot entry forms + history.
- [ ] Allocation-by-category chart component.

## Verification
- [ ] Add a snapshot to a product; confirm F008's delete-safety now blocks hard-deleting that product/its account.
- [ ] Add snapshots across multiple products/categories; confirm the allocation chart groups and sums correctly.
