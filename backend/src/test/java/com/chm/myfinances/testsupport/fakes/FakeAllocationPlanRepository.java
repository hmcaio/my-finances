package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.allocationplan.AllocationPlan;
import com.chm.myfinances.domain.allocationplan.AllocationPlanRepository;
import java.util.Optional;

/**
 * In-memory test double for {@link AllocationPlanRepository} (F026), shared across
 * application-service tests.
 */
public final class FakeAllocationPlanRepository extends InMemoryRepository<AllocationPlan>
    implements AllocationPlanRepository {

  public FakeAllocationPlanRepository() {
    super(AllocationPlan::getId);
  }

  @Override
  public Optional<AllocationPlan> findFirst() {
    return values().stream().findFirst();
  }
}
