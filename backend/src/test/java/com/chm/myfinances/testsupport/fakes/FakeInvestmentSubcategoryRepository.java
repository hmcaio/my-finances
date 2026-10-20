package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;

/**
 * In-memory test double for {@link InvestmentSubcategoryRepository}, shared across
 * application-service tests (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentSubcategoryRepository
    extends InMemoryRepository<InvestmentSubcategory> implements InvestmentSubcategoryRepository {

  public FakeInvestmentSubcategoryRepository() {
    super(InvestmentSubcategory::getId);
  }
}
