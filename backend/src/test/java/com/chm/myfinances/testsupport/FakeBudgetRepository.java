package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link BudgetRepository}, shared across application-service tests (same
 * spirit as {@link FakeCategoryRepository}).
 */
public final class FakeBudgetRepository implements BudgetRepository {

  private final Map<UUID, Budget> store = new HashMap<>();

  @Override
  public Budget save(Budget budget) {
    store.put(budget.getId(), budget);
    return budget;
  }

  @Override
  public Optional<Budget> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Budget> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return store.values().stream().anyMatch(b -> b.getCategoryId().equals(categoryId));
  }
}
