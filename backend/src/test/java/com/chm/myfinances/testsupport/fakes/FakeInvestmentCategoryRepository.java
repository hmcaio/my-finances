package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;

/**
 * In-memory test double for {@link InvestmentCategoryRepository}, shared across application-service
 * tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentCategoryRepository extends InMemoryRepository<InvestmentCategory>
    implements InvestmentCategoryRepository {

  public FakeInvestmentCategoryRepository() {
    super(InvestmentCategory::getId);
  }
}
