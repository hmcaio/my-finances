package com.chm.myfinances.infrastructure.web.budget;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;

/**
 * API representation of a {@link Budget} (F006 spec). {@code currentCap}/{@code
 * currentCapEffectiveFrom} reflect whichever {@link BudgetVersion} is effective as of the current
 * real-world month ({@link BudgetCapQuery}, resolved by {@code BudgetController}) - both are {@code
 * null} on the rare case where a Budget exists but no version is effective yet (e.g. its only
 * version's {@code effectiveFrom} is still in the future).
 */
public record BudgetResponse(
    UUID id, UUID categoryId, BigDecimal currentCap, YearMonth currentCapEffectiveFrom) {

  public static BudgetResponse from(Budget budget, Optional<BudgetVersion> currentVersion) {
    return new BudgetResponse(
        budget.getId(),
        budget.getCategoryId(),
        currentVersion.map(BudgetVersion::getMonthlyCap).orElse(null),
        currentVersion.map(BudgetVersion::getEffectiveFrom).orElse(null));
  }
}
