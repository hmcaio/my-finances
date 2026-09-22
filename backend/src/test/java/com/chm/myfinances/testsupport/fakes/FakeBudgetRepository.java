package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link BudgetRepository}, shared across application-service tests (same
 * spirit as {@link FakeCategoryRepository}).
 */
public final class FakeBudgetRepository extends InMemoryRepository<Budget>
    implements BudgetRepository {

  public FakeBudgetRepository() {
    super(Budget::getId);
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return values().stream().anyMatch(b -> b.getCategoryId().equals(categoryId));
  }
}
