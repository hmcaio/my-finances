package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link BudgetVersionRepository}, shared across application-service
 * tests (same spirit as {@link FakeTransferRepository}).
 */
public final class FakeBudgetVersionRepository implements BudgetVersionRepository {

  private final Map<UUID, BudgetVersion> store = new HashMap<>();

  @Override
  public BudgetVersion save(BudgetVersion version) {
    store.put(version.getId(), version);
    return version;
  }

  @Override
  public List<BudgetVersion> findByBudgetId(UUID budgetId) {
    return store.values().stream().filter(v -> v.getBudgetId().equals(budgetId)).toList();
  }

  @Override
  public Optional<BudgetVersion> findByBudgetIdAndEffectiveFrom(
      UUID budgetId, YearMonth effectiveFrom) {
    return store.values().stream()
        .filter(v -> v.getBudgetId().equals(budgetId) && v.getEffectiveFrom().equals(effectiveFrom))
        .findFirst();
  }
}
