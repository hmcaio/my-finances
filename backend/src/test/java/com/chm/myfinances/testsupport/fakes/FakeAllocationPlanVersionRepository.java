package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import com.chm.myfinances.domain.allocationplan.AllocationPlanVersionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory test double for {@link AllocationPlanVersionRepository} (F026), shared across
 * application-service tests (same spirit as {@link FakeBudgetVersionRepository}).
 */
public final class FakeAllocationPlanVersionRepository
    extends InMemoryRepository<AllocationPlanVersion> implements AllocationPlanVersionRepository {

  public FakeAllocationPlanVersionRepository() {
    super(AllocationPlanVersion::getId);
  }

  @Override
  public List<AllocationPlanVersion> findByPlanId(UUID planId) {
    return values().stream().filter(v -> v.getPlanId().equals(planId)).toList();
  }

  @Override
  public Optional<AllocationPlanVersion> findByPlanIdAndEffectiveFrom(
      UUID planId, YearMonth effectiveFrom) {
    return values().stream()
        .filter(v -> v.getPlanId().equals(planId) && v.getEffectiveFrom().equals(effectiveFrom))
        .findFirst();
  }
}
