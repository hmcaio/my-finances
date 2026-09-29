package com.chm.myfinances.domain.transaction;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Record-only fuel-purchase details of a {@link Transaction} (F024 spec, ADR 0021): which vehicle,
 * fuel type, liters and price per liter, plus optional odometer/km-since-last-fill. Mirrors {@code
 * InvestmentTradeDetails} (ADR 0012) in spirit, but is simpler: unlike a plain {@code Transfer}
 * that can carry an "empty" trade, a {@code Transaction} either has fuel details or doesn't -
 * presence/absence is the nullability of the whole {@link FuelDetails} field on {@code
 * Transaction}, not an internal {@code isEmpty()} state, so every field here is unconditionally
 * required except {@code kmSinceLastFill}/{@code odometer}.
 *
 * <p>Invariants: {@code vehicleId}, {@code fuelType}, {@code liters} and {@code pricePerLiter} are
 * always required together (non-null whenever a {@code FuelDetails} instance exists at all); {@code
 * liters}/{@code pricePerLiter} are {@code > 0}; {@code kmSinceLastFill}/{@code odometer}, when
 * present, are each {@code > 0}. No cross-validation against {@code Transaction.amount} (ADR 0021)
 * - real receipts round each figure to different precision than the total.
 *
 * <p>No import of {@code domain/vehicle} or {@code domain/category} (ids only) - cross-aggregate
 * checks (vehicle exists, category is the fuel category) live in the application layer, per backend
 * {@code CLAUDE.md}'s domain-isolation rule.
 */
public record FuelDetails(
    UUID vehicleId,
    FuelType fuelType,
    BigDecimal liters,
    BigDecimal pricePerLiter,
    BigDecimal kmSinceLastFill,
    BigDecimal odometer) {

  public FuelDetails {
    Objects.requireNonNull(vehicleId, "vehicleId must not be null");
    Objects.requireNonNull(fuelType, "fuelType must not be null");
    Objects.requireNonNull(liters, "liters must not be null");
    Objects.requireNonNull(pricePerLiter, "pricePerLiter must not be null");
    if (liters.signum() <= 0) {
      throw new IllegalArgumentException("liters must be positive");
    }
    if (pricePerLiter.signum() <= 0) {
      throw new IllegalArgumentException("pricePerLiter must be positive");
    }
    if (kmSinceLastFill != null && kmSinceLastFill.signum() <= 0) {
      throw new IllegalArgumentException("kmSinceLastFill must be positive when present");
    }
    if (odometer != null && odometer.signum() <= 0) {
      throw new IllegalArgumentException("odometer must be positive when present");
    }
  }
}
