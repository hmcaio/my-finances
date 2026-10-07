# F024 — Action Plan

**Depends on**: F001, F002, F004, F013, F015 (all built). See [ADR 0021](../../adr/0021-fuel-details-on-transaction.md).

## Backend
- [x] Write tests first for `Vehicle` (name required/capped, rename), then implement.
- [x] Write tests for `FuelType` and `FuelDetails` (vehicleId/fuelType/liters/pricePerLiter required together, positivity, kmSinceLastFill/odometer optional-and-positive-when-present), then implement.
- [x] Write tests for `Transaction` gaining `fuelDetails` (create/edit carry it through unchanged otherwise), then update the domain class.
- [x] Write tests for `Category` gaining `fuelCategory` (immutable post-creation like `builtIn`), then update.
- [x] Write tests for `VehicleService` (create, rename, delete blocked while referenced by a fuel transaction), then implement.
- [x] Write tests for `CategoryService.delete`'s new `fuel_category` guard (409, independent of the existing `built_in` guard), then implement.
- [x] Write tests for `TransactionService`'s fuel invariant — fuel-category transaction without `fuelDetails` rejected (400), non-fuel-category transaction with `fuelDetails` rejected (400), unknown `vehicleId` rejected (404), editing a fuel transaction's category away rejected until `fuelDetails` cleared — on create and edit, then implement.
- [x] Write tests for `FuelRatiosQuery` (kmPerLiter/amountPerKm/litersPerKm computed correctly, all null when kmSinceLastFill absent), then implement.
- [x] JPA entity/repository/adapter for `Vehicle`; extend `TransactionJpaEntity`/`CategoryJpaEntity` and their adapters for the new columns.
- [x] Flyway migration (next free `V` number): `vehicles` table; `categories.fuel_category` + partial unique index + seed (adopt-or-insert "Fuel" EXPENSE category, mirroring `V14`'s built-in-category seeding); `transactions` fuel columns + `CHECK`s. Migration test against pre-existing data (backend CLAUDE.md pattern) confirming existing transactions/categories are unaffected.
- [x] REST: vehicle endpoints, transaction DTOs/response gaining the fuel fields and computed ratios, fuel-history endpoint; controller tests; regenerate `frontend/src/api/generated/schema.ts`.
- [x] Update root `CLAUDE.md`: document `liters`/`price_per_liter` (`numeric(19,3)`) and `km_since_last_fill`/`odometer` (`numeric(19,1)`) as exceptions to the `numeric(19,2)` rule, alongside F009's `numeric(19,8)`; add `Vehicle` to the flat-taxonomy `MAX_NAME_LENGTH` list.

## Frontend
- [x] `src/api/vehicles.ts`, MSW handlers; extend `src/api/transactions.ts` for the new fields and computed ratios.
- [x] Extend the transaction form: conditional fuel fields on fuel-category selection, cleared on category change away from it.
- [x] Vehicle CRUD screen under Settings.
- [x] New Fuel page: vehicle selector, filtered transaction list, three inline-SVG time-series charts (price/liter by type, km/L, spend/km).
- [x] Nav entry for the Fuel page.

## Verification
- [x] Create a vehicle, record a fuel transaction against it with the fuel category: category picker reveals the fuel fields, saved transaction shows computed ratios, account balance drops by `amount`.
- [x] Record a second fill for the same vehicle with `kmSinceLastFill` set: ratios computed and non-null; the first fill's ratios stay null (no prior fill to diff against).
- [x] Attempt to save a fuel-category transaction with no fuel fields: rejected. Attempt to save a non-fuel-category transaction with fuel fields: rejected.
- [x] Edit an existing fuel transaction's category to a non-fuel one without clearing fuel fields: rejected; clear them first: accepted, `fuelDetails` gone.
- [x] Attempt to delete the fuel category and a vehicle with existing fuel transactions: both rejected (409).
- [x] Fuel page: charts render per-fill points for the selected vehicle only, price/liter chart shows separate lines per fuel type used.
- [x] Export a ZIP: `transactions.csv` carries the new fuel columns for fuel rows, empty for others.
