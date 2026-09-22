package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentCategoryRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentCategoryRepository extends InMemoryRepository<InvestmentCategory>
    implements InvestmentCategoryRepository {

  public FakeInvestmentCategoryRepository() {
    super(InvestmentCategory::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(c -> c.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(c -> c.getName().equals(name) && !c.getId().equals(excludedId));
  }
}
