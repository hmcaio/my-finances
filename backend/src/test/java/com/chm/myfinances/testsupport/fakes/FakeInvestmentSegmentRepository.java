package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;

/**
 * In-memory test double for {@link InvestmentSegmentRepository}, shared across application-service
 * tests (F026, same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentSegmentRepository extends InMemoryRepository<InvestmentSegment>
    implements InvestmentSegmentRepository {

  public FakeInvestmentSegmentRepository() {
    super(InvestmentSegment::getId);
  }
}
