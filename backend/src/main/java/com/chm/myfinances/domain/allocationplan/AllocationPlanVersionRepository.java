package com.chm.myfinances.domain.allocationplan;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link AllocationPlanVersion} (ADR 0004). Implemented by an adapter in
 * {@code infrastructure/persistence/allocationplan}.
 */
public interface AllocationPlanVersionRepository {

  AllocationPlanVersion save(AllocationPlanVersion version);

  /**
   * Every version for one {@code planId}, in no particular order - callers resolve the effective
   * one via {@link AllocationPlanVersion#resolveEffective}.
   */
  List<AllocationPlanVersion> findByPlanId(UUID planId);

  /**
   * The version for the exact {@code (planId, effectiveFrom)} pair, if one already exists - backs
   * {@code AllocationPlanService.setAllocation}'s same-month "replace, don't duplicate" rule.
   */
  Optional<AllocationPlanVersion> findByPlanIdAndEffectiveFrom(UUID planId, YearMonth effectiveFrom);
}
