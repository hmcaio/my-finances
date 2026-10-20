package com.chm.myfinances.domain.allocationplan;

import java.util.Optional;

/**
 * Repository port for the one-row {@link AllocationPlan} marker (ADR 0004). Implemented by an
 * adapter in {@code infrastructure/persistence/allocationplan}.
 */
public interface AllocationPlanRepository {

  AllocationPlan save(AllocationPlan plan);

  /** The single plan row, if it has been created yet ({@code AllocationPlanService} concern). */
  Optional<AllocationPlan> findFirst();
}
