# F022 — Action Plan

**Depends on**: F001, F003, F005, F008, F009, F015 (all built; F008/F009 get restructured). See [ADR 0020](../../adr/0020-investment-holdings-many-to-many.md).

## Backend
- [ ] Write tests first for `InvestmentHolding` (immutable `productId`/`accountId`, `close()` once, `additionalNotes` capped at `MAX_ADDITIONAL_NOTES_LENGTH`), then implement.
- [ ] Write tests for `InvestmentProduct`'s new shape (no `accountId`/`closedDate`, `additionalNotes` capped), then update the domain class.
- [ ] Write tests for `InvestmentSnapshot` keyed by `holdingId` (rename from `productId`, invariant unchanged), then update.
- [ ] Write tests for `InvestmentHoldingService` (create: unknown product/account 404, non-`INVESTMENT`/closed account 409, duplicate pair 409; close: already-closed 409, non-zero latest snapshot 409; delete: any history 409; editNotes), then implement.
- [ ] Write tests for `InvestmentProductService.create` as a two-write `@Transactional` use case (product + first holding; a `*ServiceTransactionalTest` proving a failed holding write rolls the product back too, per backend CLAUDE.md), then implement; update `delete` to guard on zero holdings (`InvestmentHoldingRepository.existsByProductId`) instead of `HasInvestmentHistoryChecker`; remove `close()` from this service.
- [ ] Write tests for `TransferService`'s updated investment rule (holding must exist for product+account, 404; holding closed, 409) replacing the old equality check; remove the product-move guard and its tests.
- [ ] Write tests for `LatestInvestmentSnapshotQuery` keyed by holding, and a product-level value rollup (sum across a product's holdings) used by allocation; update `AccountBalanceQuery`'s `INVESTMENT` branch to sum holdings by account; update `InvestmentSnapshotFreshnessQuery` (`needsSnapshot`) to key by holding.
- [ ] Update F009's allocation grouping tests for the rekeyed snapshot (category/sub-category sums should be unaffected in outcome, now computed via holdings) and add the "value series per product sums its holdings" case.
- [ ] JPA entity/repository/adapter for `InvestmentHolding`; update `InvestmentProductJpaEntity`/`InvestmentSnapshotJpaEntity` for the new columns.
- [ ] Flyway migration (next free `V` number): `investment_holdings` table, backfill from `investment_products`, `investment_snapshots.holding_id` backfill + constraint swap, `investment_products` column drops/uniqueness swap. Migration test against pre-existing rows (backend CLAUDE.md pattern): one holding created per product, correct `closed_date`/`account_id` carried over, snapshot `holding_id`s correct.
- [ ] REST: holding endpoints, updated product/snapshot endpoints and DTOs, updated `TransferService` error responses; controller tests; regenerate `frontend/src/api/generated/schema.ts`.
- [ ] Update root `CLAUDE.md`: `TextFieldConstraints`' javadoc and the "Free-text fields are bounded at every layer" bullet gain `InvestmentProduct`/`InvestmentHolding` as a documented exception (standalone optional notes, no mandatory description, on a taxonomy entity). Update backend `CLAUDE.md` if the migration-test/seed-collision notes need a new fixture-naming caveat.

## Frontend
- [ ] `src/api/investmentHoldings.ts`, MSW handlers; update `investmentProducts.ts`/`investmentSnapshots.ts` for moved fields/endpoints.
- [ ] `InvestmentProductsSection` (account detail): list holdings in that account instead of products; drop close/delete, link to the product page.
- [ ] `InvestmentProductDetailPage`: make holding-aware (snapshot form/history, trade history, Buy/Sell per holding); holding selector when more than one exists; add notes fields to the product form and a minimal add/edit-holding form.
- [ ] Update the Buy/Sell direction check and any other `product.accountId`/`product.closedDate` read across the frontend.

## Verification
- [ ] Create a product with an initial holding in account A; add a second holding in account B for the same product; confirm both show correct independent snapshot/trade history and the product's allocation total sums both.
- [ ] Attempt a trade in an account with no holding for that product: rejected. Close a holding with a non-zero latest snapshot: rejected; at zero: accepted, and the other holding is unaffected.
- [ ] Delete a product with one remaining (even closed, empty) holding: rejected; delete the holding first (zero history), then the product: accepted.
- [ ] Confirm net worth and the `INVESTMENT` account balance are unchanged across the migration for existing data (one product, one holding, same numbers as before).
- [ ] Confirm the category/sub-category allocation chart's totals are unchanged for existing data.
