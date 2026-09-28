# F022 — Investment Holdings

## Summary
Decouples `InvestmentProduct` from a single `Account`: introduces `InvestmentHolding` as the many-to-many link between a product and the `INVESTMENT` account(s) it's held in, so the same instrument bought at two brokers is one product with two holdings instead of two duplicate product rows. Moves `closedDate`, the close guard and `needsSnapshot` from product to holding, rekeys `InvestmentSnapshot` by holding, and adds an optional notes field to both `InvestmentProduct` and `InvestmentHolding`. Prerequisite for F023 (investments page refactor: cross-account product list, allocation by account). See [ADR 0020](../../adr/0020-investment-holdings-many-to-many.md).

## Scope
- New `InvestmentHolding` aggregate: `id`, `productId`, `accountId`, `closedDate` (nullable), `additionalNotes` (optional, `MAX_ADDITIONAL_NOTES_LENGTH`). Unique `(productId, accountId)`.
- `InvestmentProduct` becomes pure taxonomy: `id`, `name` (globally unique), `investmentCategoryId`, `investmentSubcategoryId` (nullable), `additionalNotes` (optional). Drops `accountId` and `closedDate`.
- `InvestmentSnapshot` rekeyed: `holdingId` replaces `productId`; unique `(holdingId, date)`.
- Snapshot freshness (`needsSnapshot`) and the close guard move to per-holding.
- `AccountBalanceQuery`'s `INVESTMENT` branch, F009's allocation (category/sub-category) and the value series all read through holdings instead of `product.accountId`.
- `TransferService`'s investment validation changes from "product belongs to that account" (equality) to "a holding exists for (product, that account)" (existence).
- The product-move guard (ADR 0012, F009's "Product move guard") is removed — superseded by adding/closing holdings.
- Product creation still picks one initial account and creates the product + its first holding together (`@Transactional`, two writes).
- Frontend: existing product/account detail screens and the transfer (Buy/Sell) form updated to the holding model — this feature keeps every current screen working; it does not add the new list/dashboard/charts (F023).
- Out of scope: the global filtered product list, the dashboard layout, the new sub-category/account pie charts (F023) — this feature only makes the data model support them.

## Decisions
- **Holdings are created explicitly.** Recording a trade for a (product, account) pair that has no holding yet is rejected (`404`/`409`), not auto-created — a trade shouldn't silently create domain state.
- **Product name uniqueness moves from per-account to global.** The scenario the old per-account uniqueness protected (same instrument, two brokers) is now one product with two holdings, so two *different* instruments sharing a name is the only remaining collision — same as every other flat-taxonomy entity in this codebase.
- **`InvestmentProduct.additionalNotes` and `InvestmentHolding.additionalNotes` are both optional-only fields** (`MAX_ADDITIONAL_NOTES_LENGTH`, 500) — a deliberate exception to `TextFieldConstraints`' documented pattern (mandatory description + optional notes, non-taxonomy entities only): here there's no paired mandatory description, and the product is a taxonomy entity. Product notes are a remark about the instrument ("matures 2029, tax-exempt"); holding notes are a remark about that specific holding ("bought via XP promo").
- **"Move a product to another account" is removed, not migrated.** With multiple holdings, moving is meaningless — add a new holding at the destination and close the old one if desired. Both keep independent history.
- **A product can only be hard-deleted with zero holdings** (not zero history — even a closed, empty holding still counts and must be removed first). A holding follows the old product rule: hard-delete only with zero history (no snapshot, no tagged trade), otherwise close.

## Backend

### Domain
- `domain/investmentholding/InvestmentHolding.java`: `id`, `productId`, `accountId` (both immutable post-creation — re-pointing either would misattribute existing snapshots/trades), `closedDate` (nullable, `close()` sets it once), `additionalNotes` (nullable, capped, `editNotes(...)` mutator). No import of `domain/investmentproduct` or `domain/account` (cross-aggregate checks live in the application service).
- `domain/investmentproduct/InvestmentProduct.java`: drop `accountId`, `closedDate`, `close()`; add `additionalNotes` (nullable, capped), folded into the existing `edit(...)` full-replace mutator. `name`/category/sub-category invariants unchanged.
- `domain/investmentsnapshot/InvestmentSnapshot.java`: field renamed `productId` → `holdingId`; `balance` invariant unchanged.
- `domain/investmentproduct/HasInvestmentHistoryChecker` (F008) port: repurposed to ask "does this product have any holding at all" for the product delete guard. A new `domain/investmentholding/HasHoldingHistoryChecker` port covers the holding delete guard (a snapshot row, or a transfer tagged with this product *and* this holding's account).

### Application
- New `InvestmentHoldingService`: `create(productId, accountId, notes)` — 404 if product or account unknown, 409 if the account isn't an open `INVESTMENT` account (`InvestmentAccountRequiredException`, reused from F008), 409 on a duplicate `(productId, accountId)` pair (`InvestmentHoldingAlreadyExistsException`); `editNotes(holdingId, notes)`; `close(holdingId)` — 409 if already closed or if the latest snapshot is non-zero (`InvestmentHoldingNotEmptyException`, same rule F009 had on the product); `delete(holdingId)` — 409 unless zero history (`InvestmentHoldingHasHistoryException`); `findByProduct(productId)`, `findByAccount(accountId)`.
- `InvestmentProductService.create` becomes a two-write, `@Transactional` use case: create the product, then create its first holding via `InvestmentHoldingService` (backend CLAUDE.md "Transactions" rule — a failure between the two must not leave a holding-less product or an orphaned holding). `close()` is removed from this service (holdings close, not products). `delete()`'s guard becomes "zero holdings", via a new `InvestmentHoldingRepository.existsByProductId`.
- `TransferService` (F009): the rule "an endpoint that is an `INVESTMENT` account requires `investmentProductId`, and the product must belong to that account" becomes "...and an open, non-closed holding must exist for `(investmentProductId, that account)`" — `404 InvestmentHoldingNotFoundException` if none, `409 InvestmentHoldingClosedException` if closed. Direction derivation (destination = buy, source = sell) is unchanged.
- `LatestInvestmentSnapshotQuery`: keyed by `holdingId` now (`latestByHolding`, `latestOf(holdingId)`). A product-level value rollup (sum across a product's holdings) feeds the category/sub-category allocation.
- `AccountBalanceQuery`'s `INVESTMENT` branch: sum the latest snapshots of holdings where `accountId` = this account (via `InvestmentHoldingRepository.findByAccountId` + `LatestInvestmentSnapshotQuery`), replacing the old "this account's products" join.
- `InvestmentSnapshotFreshnessQuery` (`needsSnapshot`): keyed by `holdingId`; a holding needs a snapshot when a transfer tagged with its product *and* its account is dated after the holding's latest snapshot, or has no snapshot at all.
- Product-move guard (`InvestmentProductMoveBlockedException`, F009) and its check in `edit` are deleted — there's no `accountId` on the product to move anymore.
- Reclassifying a product (category/sub-category) is unaffected — still an application-layer full-replace, still regroups past allocation by current classification.

### Persistence
- New migration (check the highest existing `V` number first):
  1. `investment_holdings`: `id uuid pk`, `product_id uuid not null references investment_products`, `account_id uuid not null references accounts` (both indexed), `closed_date date`, `additional_notes varchar(500)`, `UNIQUE (product_id, account_id)`, audit columns.
  2. Backfill: one `investment_holdings` row per existing `investment_products` row, copying `account_id`/`closed_date` (unambiguous today, since the model is currently 1:1).
  3. `investment_snapshots`: add `holding_id uuid references investment_holdings`, backfill it via a join to the holding just created for that product, `ALTER COLUMN holding_id SET NOT NULL`, drop `UNIQUE (product_id, date)`, add `UNIQUE (holding_id, date)`, drop `product_id`.
  4. `investment_products`: add `additional_notes varchar(500)`, drop `UNIQUE (account_id, name)`, add `UNIQUE (name)`, drop `closed_date`, drop `account_id` (its FK/index first).
  5. `transfers`: unchanged — `investment_product_id` stays; no `investment_holding_id` column (the holding is looked up by `(investment_product_id, the trade's INVESTMENT-side account)` at validation time, not stored, since a trade already has both).
- Migration test (backend CLAUDE.md pattern, throwaway schema + hand-run Flyway): a pre-existing product/snapshot pair ends up with exactly one holding, the snapshot's `holding_id` points at it, and the product's old `account_id`/`closed_date` values are preserved on the holding.
- Test the `UNIQUE (product_id, account_id)` and `UNIQUE (holding_id, date)` constraints against real Postgres.

### API
- `POST /api/investment-holdings` (`{productId, accountId, additionalNotes}`), `GET /api/investment-holdings?productId=` and `?accountId=`, `PATCH /api/investment-holdings/{id}` (`{additionalNotes}`), `POST /api/investment-holdings/{id}/close`, `DELETE /api/investment-holdings/{id}`.
- `POST /api/investment-products` still takes `accountId` (creates the first holding); the response drops `accountId`/`closedDate`/`hasHistory` (these move to the holding response) and gains `additionalNotes`. `GET /api/investment-products` drops the `?accountId=` filter (no longer meaningful on the product) — F023 adds real filtering.
- The investment holding response carries `productId`, `accountId`, `closedDate`, `additionalNotes`, `needsSnapshot`, `latestSnapshot` (moved from the old product response), `hasHistory`.
- Snapshot endpoints move from `/api/investment-products/{id}/snapshots` to `/api/investment-holdings/{id}/snapshots` (same shapes otherwise).
- Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- `src/api/investmentHoldings.ts` + MSW handlers; `investmentProducts.ts`/`investmentSnapshots.ts` updated for the moved fields/endpoints.
- Product creation form unchanged in shape (still asks for one account).
- `InvestmentProductsSection` (account detail page): now lists **holdings** in that account (product name joined client-side from the products list, latest balance, `needsSnapshot`), links to the product's page for management; loses its own close/delete actions (those move to the holding).
- `InvestmentProductDetailPage`: becomes holding-aware — snapshot form/history, trade history and Buy/Sell now operate per holding. With exactly one holding the page reads the same as today; with more than one, a holding selector (or a section per holding) is added, and the Buy/Sell direction check moves from `trade.toAccountId === product.accountId` to a holding lookup. This is the minimum needed to keep the feature usable — F023 turns this into the full multi-holding management page.
- Notes fields added to the product form and a (new, minimal) add/edit-holding form.

## Dependencies
F001, F003 (`AccountBalanceQuery`), F005 (transfers), F008 (products/taxonomy — this feature restructures it), F009 (snapshots/trades/allocation — this feature rekeys it), F015.
