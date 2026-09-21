package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentCategoryRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentCategoryRepository implements InvestmentCategoryRepository {

  private final Map<UUID, InvestmentCategory> store = new HashMap<>();

  @Override
  public InvestmentCategory save(InvestmentCategory category) {
    store.put(category.getId(), category);
    return category;
  }

  @Override
  public Optional<InvestmentCategory> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<InvestmentCategory> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return store.containsKey(id);
  }

  @Override
  public boolean existsByName(String name) {
    return store.values().stream().anyMatch(c -> c.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(c -> c.getName().equals(name) && !c.getId().equals(excludedId));
  }
}
