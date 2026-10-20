package com.chm.myfinances.application.transaction;

import java.math.BigDecimal;

/**
 * Computed-on-read ratios for a fuel-purchase {@link
 * com.chm.myfinances.domain.transaction.Transaction} (F024 spec, ADR 0021). Every field is {@code
 * null} when the transaction's {@code kmSinceLastFill} is absent (a vehicle's first recorded fill
 * has nothing to diff against).
 */
public record FuelRatios(BigDecimal kmPerLiter, BigDecimal amountPerKm, BigDecimal litersPerKm) {

  private static final FuelRatios EMPTY = new FuelRatios(null, null, null);

  /**
   * No ratios at all - {@code kmSinceLastFill} was absent, or the transaction has no fuel details.
   */
  public static FuelRatios empty() {
    return EMPTY;
  }
}
