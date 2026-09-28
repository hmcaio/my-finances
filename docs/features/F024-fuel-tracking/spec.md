# F024 — Fuel Tracking

## Summary
Adds car fuel-purchase tracking: a `Vehicle` taxonomy and an optional `FuelDetails` value object on `Transaction`, gated by a dedicated `fuel_category` flag on `Category` (ADR 0021), distinct from the existing `built_in` one. Records liters, price per liter, odometer and km since the last fill per vehicle, and derives km/L, spend/km and L/km ratios on read, charted per vehicle over time (PRD §5.11, §6.11).

## Scope
- New `Vehicle` aggregate: `id`, `name`. Flat taxonomy, CRUD, delete blocked while referenced by any fuel transaction — same pattern as Category/PaymentMethod/Institution.
- `Category` gains `fuel_category` (boolean, independent of `built_in`, at most one row). Seeded once via migration onto an existing or new EXPENSE category named "Fuel". Delete and rename both blocked while set.
- `Transaction` gains optional `FuelDetails`: `vehicleId`, `fuelType` (fixed enum: ETANOL, ETANOL_ADITIVADO, GASOLINA, GASOLINA_ADITIVADA), `liters`, `pricePerLiter`, `kmSinceLastFill` (nullable), `odometer` (nullable). Present if and only if the transaction's category is the fuel category.
- Derived, computed on read: km/L = `kmSinceLastFill / liters`; spend/km = `amount / kmSinceLastFill`; L/km = `liters / kmSinceLastFill`. Null when `kmSinceLastFill` is absent (a vehicle's first recorded fill).
- Fuel fields shown conditionally in the existing Transaction create/edit form when the fuel category is selected.
- New Fuel page: per-vehicle filtered transaction list plus three time-series charts (price/liter by fuel type, km/L, spend/km), raw per-fill points, no aggregation.
- Vehicle CRUD under Settings, alongside Category/PaymentMethod/Institution.
- CSV export (F013): `transactions.csv` gains `vehicle_id`/`vehicle_name`, `fuel_type`, `liters`, `price_per_liter`, `km_since_last_fill`, `odometer` (empty for non-fuel rows).
- Out of scope: custom/user-editable fuel types (fixed enum only, PRD §3 non-goal); vehicle value/depreciation in net worth (PRD §3 non-goal, unchanged — `Vehicle` here is a plain label, not a valued asset); cross-vehicle aggregate charts; auto-computing `kmSinceLastFill` from odometer deltas.

## Decisions
See [ADR 0021](../../adr/0021-fuel-details-on-transaction.md) for the full rationale. Summary:
- **`FuelDetails` extends `Transaction`, not a new aggregate** — mirrors ADR 0012's `Transfer` + `InvestmentTradeDetails`, now applied to `Transaction` since fuel spend is an ordinary expense (unlike an investment trade).
- **A dedicated `fuel_category` flag, not `built_in`** — `built_in` already means something else (delete-blocked but renamable, one slot per `CategoryType` already taken for EXPENSE) and doesn't structurally guarantee the fuel-details invariant since it allows rename.
- **No cross-validation** between `amount`, `liters` and `pricePerLiter` — real receipts round each to different precision than the total; `amount` alone drives balances.
- **`kmSinceLastFill` and `odometer` are both optional** — the former has no value to diff against on a vehicle's first fill, the latter is purely informational and may not always be read/entered.
- **Editing a fuel transaction's category away from the fuel category is rejected** until `FuelDetails` is cleared first — no silent data loss.
- **Charts always scoped to one selected vehicle** — km-based ratios are meaningless mixed across vehicles.

## Backend

### Domain
- `domain/vehicle/Vehicle.java`: `id`, `name` (capped `MAX_NAME_LENGTH`, non-blank, domain-constructor-checked). `rename(...)` mutator. No import of any other aggregate.
- `domain/transaction/FuelType.java`: enum `ETANOL`, `ETANOL_ADITIVADO`, `GASOLINA`, `GASOLINA_ADITIVADA`.
- `domain/transaction/FuelDetails.java`: record `vehicleId`, `fuelType`, `liters`, `pricePerLiter`, `kmSinceLastFill`, `odometer`. Invariants: `vehicleId`, `fuelType`, `liters`, `pricePerLiter` always required together; `liters`/`pricePerLiter` `> 0`; `kmSinceLastFill`/`odometer`, when present, `> 0`. No import of `domain/vehicle` or `domain/category` (ids only — cross-aggregate checks live in the application service, per backend CLAUDE.md's domain-isolation rule).
- `domain/transaction/Transaction.java` (F004): gains nullable `fuelDetails`; the existing full-replace `edit(...)` mutator takes it too.
- `domain/category/Category.java` (F002): gains `fuelCategory` (boolean, immutable after creation like `builtIn`).

### Application
- New `VehicleService`: `create`/`rename`/`findAll`/`delete`. `delete` is `409` (`VehicleInUseException`) while any transaction references it (`transactionRepository.existsByFuelDetailsVehicleId`), same shape as `CategoryService.delete`'s in-use check.
- `CategoryService`: `delete` gains a `fuel_category` guard (`FuelCategoryException`, `409`) parallel to (not reusing) the existing `BuiltInCategoryException` check.
- `TransactionService` (F004): create/edit validate the fuel invariant — category is the fuel category XOR `fuelDetails` is null is rejected (`FuelDetailsCategoryMismatchException`, `400`, since it's a self-contained request-shape error, not a state-dependent one); when `fuelDetails` is present, `vehicleId` must reference an existing `Vehicle` (`404`).
- `FuelRatiosQuery` (application-layer, read-only, matching `AccountBalanceQuery`'s "query object for a computed-not-stored value" pattern): given a fuel `Transaction`, returns `kmPerLiter`, `amountPerKm`, `litersPerKm` — all `null` when `kmSinceLastFill` is absent. Used by the Fuel page's per-vehicle history endpoint.

### Persistence
- New migration (check the highest existing `V` number first):
  1. `vehicles`: `id uuid pk`, `name varchar(100) not null unique`, audit columns.
  2. `categories`: add `fuel_category boolean not null default false`, partial unique index `uq_categories_single_fuel_category ON categories (fuel_category) WHERE fuel_category` (mirrors `uq_categories_single_built_in_per_type`, but unscoped by type since there's only ever one). Seed: flag an existing "Fuel"-named EXPENSE category if present, else insert a fresh EXPENSE category named "Fuel" with `fuel_category = true` (same adopt-or-insert pattern `V14` used for the built-in categories).
  3. `transactions`: add `vehicle_id uuid references vehicles`, `fuel_type varchar(30)`, `liters numeric(19,3)`, `price_per_liter numeric(19,3)`, `km_since_last_fill numeric(19,1)`, `odometer numeric(19,1)`. `CHECK (liters IS NULL OR liters > 0)`, `CHECK (price_per_liter IS NULL OR price_per_liter > 0)`, `CHECK (km_since_last_fill IS NULL OR km_since_last_fill > 0)`, `CHECK (odometer IS NULL OR odometer > 0)`, `CHECK ((vehicle_id IS NULL) = (fuel_type IS NULL))`, `CHECK ((fuel_type IS NULL) = (liters IS NULL))`, `CHECK ((liters IS NULL) = (price_per_liter IS NULL))`. Existing rows satisfy all of these (new columns null).
- `liters`/`price_per_liter` at `numeric(19,3)` and `km_since_last_fill`/`odometer` at `numeric(19,1)` are deliberate exceptions to the root CLAUDE.md `numeric(19,2)` rule, alongside F009's `numeric(19,8)` — document them there.
- DTOs bound scale with `@Digits` on the four numeric fields, so an out-of-range number is `400`, not a database error.

### API
- `POST /api/vehicles` (`{name}`), `GET /api/vehicles`, `PATCH /api/vehicles/{id}` (`{name}`), `DELETE /api/vehicles/{id}`.
- Transaction create/update bodies (F004) gain optional `vehicleId`, `fuelType`, `liters`, `pricePerLiter`, `kmSinceLastFill`, `odometer`. Response gains the same fields (null when absent) plus computed `kmPerLiter`, `amountPerKm`, `litersPerKm`.
- `GET /api/vehicles/{id}/fuel-history?from=&to=` → transactions for that vehicle with fuel details and ratios, ordered by date, for the Fuel page's list and charts (`from`/`to` optional date bounds; unbounded by default).
- Regenerate `frontend/src/api/generated/schema.ts`.

## Frontend
- `src/api/vehicles.ts` + MSW handlers; extend `src/api/transactions.ts` for the new fields.
- Transaction form (F004): when the fuel category is selected, reveal vehicle select, fuel type select, liters, price per liter, optional km since last fill, optional odometer. Selecting a different category while these are filled clears them (mirrors the backend rejecting the mismatch, so the form never submits an invalid combination); the fuel category can also be pre-selected when arriving from the Fuel page's "add" action.
- New Fuel page (`/fuel`): vehicle selector, per-vehicle fuel transaction list (reusing the existing transaction list primitives), and three hand-rolled inline-SVG time series charts (matching `NetWorthTrendChart`/`ValueSeriesChart`, no charting library): price per liter (one line per fuel type, legend), km per liter, spend per km — raw per-fill points, no date aggregation.
- Vehicle CRUD screen under Settings, alongside Category/PaymentMethod/Institution's existing pattern.
- Nav entry for the new Fuel page.

## Dependencies
F001, F002 (Category), F004 (Transaction), F013 (export gains the new columns), F015.
