package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.budget.BudgetVersion;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Test data builder for {@link BudgetVersion} (issue #31, B6). See {@link BudgetMother} for the
 * owning budget.
 */
public final class BudgetVersionMother {

  private UUID id = UUID.randomUUID();
  private UUID budgetId = UUID.randomUUID();
  private BigDecimal monthlyCap = new BigDecimal("500.00");
  private YearMonth effectiveFrom = YearMonth.now();

  private BudgetVersionMother() {}

  public static BudgetVersionMother version() {
    return new BudgetVersionMother();
  }

  public BudgetVersionMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public BudgetVersionMother withBudgetId(UUID budgetId) {
    this.budgetId = budgetId;
    return this;
  }

  public BudgetVersionMother withMonthlyCap(BigDecimal monthlyCap) {
    this.monthlyCap = monthlyCap;
    return this;
  }

  public BudgetVersionMother withEffectiveFrom(YearMonth effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
    return this;
  }

  public BudgetVersion build() {
    return BudgetVersion.create(id, budgetId, monthlyCap, effectiveFrom);
  }
}
