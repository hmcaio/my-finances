-- F024: Fuel tracking (ADR 0021).
--
-- 1. `vehicles`: a new flat taxonomy, same shape as `payment_methods` - name bounded and UNIQUE
--    from the start (the V3/V10 lesson).
-- 2. `categories.fuel_category`: a second, independent flag alongside `built_in` (V14) - at most
--    one row (unscoped by type, unlike `built_in`'s one-per-CategoryType partial index, since
--    there's only ever a single fuel category regardless of type). Seed: adopt an existing
--    EXPENSE row named "Fuel" if one exists, else insert a fresh one - same adopt-or-insert
--    pattern V14 used for the built-in categories, simpler here since there's no rename-on-adopt
--    step (the name "Fuel" is what's being adopted BY, not renamed away from).
-- 3. `transactions` fuel columns: nullable, so every existing row stays valid untouched. CHECKs
--    mirror FuelDetails/Transaction: liters/pricePerLiter/kmSinceLastFill/odometer positive when
--    present, and vehicle_id/fuel_type/liters/pricePerLiter all null together or all non-null
--    together (kmSinceLastFill/odometer are independently optional, no CHECK needed for those).
--
-- numeric(19,3) for liters/price_per_liter and numeric(19,1) for km_since_last_fill/odometer are
-- deliberate exceptions to the root CLAUDE.md numeric(19,2) money rule (documented there),
-- alongside F009's numeric(19,8) trade quantity/price.
--
-- Numbered V18: V17 (investment holdings) was the highest when this was written.

CREATE TABLE vehicles (
    id                uuid PRIMARY KEY,
    name              varchar(100) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_modified_at  timestamptz NOT NULL,
    CONSTRAINT uq_vehicles_name UNIQUE (name)
);

ALTER TABLE categories ADD COLUMN fuel_category boolean NOT NULL DEFAULT false;

CREATE UNIQUE INDEX uq_categories_single_fuel_category ON categories (fuel_category)
    WHERE fuel_category;

-- Adopt an existing EXPENSE "Fuel" row if present.
UPDATE categories
SET fuel_category = true, last_modified_at = now()
WHERE type = 'EXPENSE' AND name = 'Fuel';

-- Otherwise insert a fresh one.
INSERT INTO categories (id, name, type, fuel_category, created_at, last_modified_at)
SELECT gen_random_uuid(), 'Fuel', 'EXPENSE', true, now(), now()
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE fuel_category);

ALTER TABLE transactions
    ADD COLUMN vehicle_id uuid REFERENCES vehicles (id),
    ADD COLUMN fuel_type varchar(30),
    ADD COLUMN liters numeric(19,3),
    ADD COLUMN price_per_liter numeric(19,3),
    ADD COLUMN km_since_last_fill numeric(19,1),
    ADD COLUMN odometer numeric(19,1);

ALTER TABLE transactions ADD CONSTRAINT chk_transactions_liters_positive
    CHECK (liters IS NULL OR liters > 0);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_price_per_liter_positive
    CHECK (price_per_liter IS NULL OR price_per_liter > 0);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_km_since_last_fill_positive
    CHECK (km_since_last_fill IS NULL OR km_since_last_fill > 0);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_odometer_positive
    CHECK (odometer IS NULL OR odometer > 0);
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_vehicle_with_fuel_type
    CHECK ((vehicle_id IS NULL) = (fuel_type IS NULL));
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_fuel_type_with_liters
    CHECK ((fuel_type IS NULL) = (liters IS NULL));
ALTER TABLE transactions ADD CONSTRAINT chk_transactions_liters_with_price_per_liter
    CHECK ((liters IS NULL) = (price_per_liter IS NULL));

CREATE INDEX idx_transactions_vehicle_id ON transactions (vehicle_id);
