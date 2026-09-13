# F009 — Action Plan

**Depends on**: F001, F008.

## Backend
- [ ] Add `domain/investmentbuysell/InvestmentBuySellLog.java`, `domain/investmentsnapshot/InvestmentSnapshot.java`.
- [ ] Add JPA entities (extend `AuditableEntity`), repositories, adapters.
- [ ] Flyway migration `V9__investment_buysell_and_snapshots.sql`.
- [ ] Implement F008's `HasInvestmentHistoryChecker` port against these two tables.
- [ ] `LatestInvestmentSnapshotQuery` application service (latest snapshot per product as of a date).
- [ ] Allocation-by-category application service, grouping the above by `InvestmentCategory`.
- [ ] REST controllers + DTOs for logs, snapshots, and the allocation endpoint.
- [ ] Tests: history checker returns `true` once either table has a row for the product, allocation grouping correctness, "latest as of date" resolution when multiple snapshots exist.

## Frontend
- [ ] `src/api/investmentBuySellLogs.ts`, `src/api/investmentSnapshots.ts`, `src/api/investmentAllocation.ts`.
- [ ] Extend F008's investment product detail view with buy/sell log and snapshot entry forms + history.
- [ ] Allocation-by-category chart component.

## Verification
- [ ] Add a snapshot to a product; confirm F008's delete-safety now blocks hard-deleting that product/its account.
- [ ] Add snapshots across multiple products/categories; confirm the allocation chart groups and sums correctly.
