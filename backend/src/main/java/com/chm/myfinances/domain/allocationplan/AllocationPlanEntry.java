package com.chm.myfinances.domain.allocationplan;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of an {@link AllocationPlanVersion} (F026 spec, ADR 0023): a target percentage for one
 * {@code InvestmentProduct}, held by id only - this package never imports {@code
 * domain.investmentproduct}. That the product exists and is classified under the "REITs (FIIs)"
 * sub-category is an {@code AllocationPlanService} concern (cross-aggregate check, same layering as
 * every other such check in this codebase).
 *
 * <p>Invariant: {@code targetPercentage} is always {@code > 0}. The "entries of one version sum to
 * exactly 100" and "no duplicate product within a version" invariants span multiple entries, so
 * they're checked by {@link AllocationPlanVersion}, not here.
 */
public record AllocationPlanEntry(UUID investmentProductId, BigDecimal targetPercentage) {

  public AllocationPlanEntry {
    Objects.requireNonNull(investmentProductId, "investmentProductId must not be null");
    Objects.requireNonNull(targetPercentage, "targetPercentage must not be null");
    if (targetPercentage.signum() <= 0) {
      throw new IllegalArgumentException("targetPercentage must be positive");
    }
  }
}
