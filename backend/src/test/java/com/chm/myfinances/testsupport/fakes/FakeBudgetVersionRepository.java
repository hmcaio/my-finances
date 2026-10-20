package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link BudgetVersionRepository}, shared across application-service
 * tests (same spirit as {@link FakeTransferRepository}).
 */
public final class FakeBudgetVersionRepository extends InMemoryRepository<BudgetVersion>
    implements BudgetVersionRepository {

  public FakeBudgetVersionRepository() {
    super(BudgetVersion::getId);
  }

  @Override
  public List<BudgetVersion> findByBudgetId(UUID budgetId) {
    return values().stream().filter(v -> v.getBudgetId().equals(budgetId)).toList();
  }

  @Override
  public Optional<BudgetVersion> findByBudgetIdAndEffectiveFrom(
      UUID budgetId, YearMonth effectiveFrom) {
    return values().stream()
        .filter(v -> v.getBudgetId().equals(budgetId) && v.getEffectiveFrom().equals(effectiveFrom))
        .findFirst();
  }
}
