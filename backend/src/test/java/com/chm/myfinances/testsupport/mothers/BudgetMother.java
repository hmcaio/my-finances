package com.chm.myfinances.testsupport.mothers;

import com.chm.myfinances.domain.budget.Budget;
import java.util.UUID;

/**
 * Test data builder for {@link Budget} (issue #31, B6). See {@link BudgetVersionMother} for its
 * version.
 */
public final class BudgetMother {

  private UUID id = UUID.randomUUID();
  private UUID categoryId = UUID.randomUUID();

  private BudgetMother() {}

  public static BudgetMother budget() {
    return new BudgetMother();
  }

  public BudgetMother withId(UUID id) {
    this.id = id;
    return this;
  }

  public BudgetMother withCategoryId(UUID categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  public Budget build() {
    return Budget.create(id, categoryId);
  }
}
