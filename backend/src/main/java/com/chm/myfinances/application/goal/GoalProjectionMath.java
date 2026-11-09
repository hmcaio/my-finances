package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The rate math behind a goal projection (F039 spec, ADR 0034), run in {@code double} because the
 * monthly rate needs a fractional power and {@code BigDecimal} has no such {@code pow}. Money
 * results are rounded to two decimals only at the edges, so the series stays internally consistent.
 *
 * <p>Month {@code m} (from 1): {@code V[m] = V[m-1] x (1 + i) + C}, with {@code i = (1 +
 * r/100)^(1/12) - 1} and the contribution {@code C} at month end, starting from {@code V[0]}. The
 * closed form of the same loop gives the contribution that reaches a target in {@code N} months.
 */
final class GoalProjectionMath {

  /** 50 years: the longest a dateless goal is simulated (ADR 0034). */
  static final int MAX_MONTHS = 600;

  private GoalProjectionMath() {}

  /** The monthly growth rate equivalent to a real annual return in percent. */
  static double monthlyRate(BigDecimal annualReturnPercent) {
    return Math.pow(1 + annualReturnPercent.doubleValue() / 100.0, 1.0 / 12.0) - 1;
  }

  /** The values of month 0 through {@code months} (so {@code months + 1} entries). */
  static double[] simulate(
      double startingValue, double monthlyRate, double contribution, int months) {
    double[] values = new double[months + 1];
    values[0] = startingValue;
    for (int m = 1; m <= months; m++) {
      values[m] = values[m - 1] * (1 + monthlyRate) + contribution;
    }
    return values;
  }

  /**
   * The monthly contribution that makes {@code startingValue} reach {@code target} after {@code
   * months}: {@code max(0, (T - V0 x (1+i)^N) / s)} with {@code s = ((1+i)^N - 1) / i}, or {@code
   * N} when {@code i = 0}. Rounded up to the cent so contributing it certainly reaches the target.
   * {@code null} when there are no months left and the target is not met (nothing can help).
   */
  static BigDecimal requiredContribution(
      double startingValue, double monthlyRate, int months, double target) {
    if (months == 0) {
      return startingValue >= target ? money(0) : null;
    }
    double growth = Math.pow(1 + monthlyRate, months);
    double annuity = monthlyRate == 0 ? months : (growth - 1) / monthlyRate;
    double needed = (target - startingValue * growth) / annuity;
    if (needed <= 0) {
      return money(0);
    }
    return BigDecimal.valueOf(needed)
        .setScale(6, RoundingMode.HALF_UP)
        .setScale(2, RoundingMode.CEILING);
  }

  /** A value rounded to cents, {@code HALF_UP}. */
  static BigDecimal money(double value) {
    return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
  }
}
