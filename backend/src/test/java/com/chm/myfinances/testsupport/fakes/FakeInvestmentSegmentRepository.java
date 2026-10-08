package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link InvestmentSegmentRepository}, shared across application-service
 * tests (F026, same spirit as {@link FakeIdGenerator}).
 */
public final class FakeInvestmentSegmentRepository extends InMemoryRepository<InvestmentSegment>
    implements InvestmentSegmentRepository {

  public FakeInvestmentSegmentRepository() {
    super(InvestmentSegment::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(s -> s.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(s -> s.getName().equals(name) && !s.getId().equals(excludedId));
  }
}
