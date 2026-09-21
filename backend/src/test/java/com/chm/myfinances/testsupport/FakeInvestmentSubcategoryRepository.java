package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentSubcategoryRepository}, shared across
 * application-service tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentSubcategoryRepository implements InvestmentSubcategoryRepository {

  private final Map<UUID, InvestmentSubcategory> store = new HashMap<>();

  @Override
  public InvestmentSubcategory save(InvestmentSubcategory subcategory) {
    store.put(subcategory.getId(), subcategory);
    return subcategory;
  }

  @Override
  public Optional<InvestmentSubcategory> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<InvestmentSubcategory> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return store.values().stream()
        .anyMatch(s -> s.getInvestmentCategoryId().equals(investmentCategoryId));
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndName(UUID investmentCategoryId, String name) {
    return store.values().stream()
        .anyMatch(
            s ->
                s.getInvestmentCategoryId().equals(investmentCategoryId)
                    && s.getName().equals(name));
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndNameAndIdNot(
      UUID investmentCategoryId, String name, UUID excludedId) {
    return store.values().stream()
        .anyMatch(
            s ->
                s.getInvestmentCategoryId().equals(investmentCategoryId)
                    && s.getName().equals(name)
                    && !s.getId().equals(excludedId));
  }
}
