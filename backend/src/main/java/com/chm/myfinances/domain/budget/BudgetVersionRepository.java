package com.chm.myfinances.domain.budget;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link BudgetVersion} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/budget}.
 */
public interface BudgetVersionRepository {

  BudgetVersion save(BudgetVersion version);

  /**
   * Every version for one {@code budgetId}, in no particular order - callers resolve the effective
   * one via {@link BudgetVersion#resolveEffective}.
   */
  List<BudgetVersion> findByBudgetId(UUID budgetId);

  /**
   * The version for the exact {@code (budgetId, effectiveFrom)} pair, if one already exists - backs
   * {@code BudgetService.setCap}'s same-month "replace, don't duplicate" rule (F006 spec).
   */
  Optional<BudgetVersion> findByBudgetIdAndEffectiveFrom(UUID budgetId, YearMonth effectiveFrom);
}
