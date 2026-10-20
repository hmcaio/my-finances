package com.chm.myfinances.application.transaction;

import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes km/L, spend/km and L/km for a fuel {@link Transaction} (F024 spec, ADR 0021) - a pure
 * function of the transaction's own {@code amount}/{@code fuelDetails}, matching {@code
 * AccountBalanceQuery}'s "query object for a computed-not-stored value" pattern in spirit. Unlike
 * that query, this one needs no repository (everything it reads lives on the one {@link
 * Transaction} passed in), so it's a plain static utility rather than a Spring bean - {@code
 * TransactionResponse.from(...)} calls it directly, the same static-factory shape every other
 * response DTO in this codebase already uses.
 *
 * <p>Rounded to {@value #SCALE} decimal places, half-up - these are display-only ratios, never fed
 * back into a stored amount.
 */
public final class FuelRatiosQuery {

  private static final int SCALE = 4;

  private FuelRatiosQuery() {}

  /**
   * {@link FuelRatios#empty()} when {@code transaction} has no fuel details, or has fuel details
   * but no {@code kmSinceLastFill} (a vehicle's first recorded fill).
   */
  public static FuelRatios ratiosFor(Transaction transaction) {
    FuelDetails details = transaction.getFuelDetails();
    if (details == null || details.kmSinceLastFill() == null) {
      return FuelRatios.empty();
    }
    BigDecimal km = details.kmSinceLastFill();
    BigDecimal kmPerLiter = km.divide(details.liters(), SCALE, RoundingMode.HALF_UP);
    BigDecimal amountPerKm = transaction.getAmount().divide(km, SCALE, RoundingMode.HALF_UP);
    BigDecimal litersPerKm = details.liters().divide(km, SCALE, RoundingMode.HALF_UP);
    return new FuelRatios(kmPerLiter, amountPerKm, litersPerKm);
  }
}
