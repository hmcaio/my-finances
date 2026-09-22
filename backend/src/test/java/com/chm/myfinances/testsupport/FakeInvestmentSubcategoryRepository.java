package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentSubcategoryRepository}, shared across
 * application-service tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentSubcategoryRepository
    extends InMemoryRepository<InvestmentSubcategory> implements InvestmentSubcategoryRepository {

  public FakeInvestmentSubcategoryRepository() {
    super(InvestmentSubcategory::getId);
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return values().stream()
        .anyMatch(s -> s.getInvestmentCategoryId().equals(investmentCategoryId));
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndName(UUID investmentCategoryId, String name) {
    return values().stream()
        .anyMatch(
            s ->
                s.getInvestmentCategoryId().equals(investmentCategoryId)
                    && s.getName().equals(name));
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndNameAndIdNot(
      UUID investmentCategoryId, String name, UUID excludedId) {
    return values().stream()
        .anyMatch(
            s ->
                s.getInvestmentCategoryId().equals(investmentCategoryId)
                    && s.getName().equals(name)
                    && !s.getId().equals(excludedId));
  }
}
