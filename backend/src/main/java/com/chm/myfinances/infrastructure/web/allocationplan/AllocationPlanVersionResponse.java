package com.chm.myfinances.infrastructure.web.allocationplan;

import com.chm.myfinances.domain.allocationplan.AllocationPlanVersion;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** API representation of an {@link AllocationPlanVersion} (F026 spec). */
public record AllocationPlanVersionResponse(
    UUID id, UUID planId, List<AllocationPlanEntryResponse> entries, YearMonth effectiveFrom) {

  public static AllocationPlanVersionResponse from(AllocationPlanVersion version) {
    return new AllocationPlanVersionResponse(
        version.getId(),
        version.getPlanId(),
        version.getEntries().stream().map(AllocationPlanEntryResponse::from).toList(),
        version.getEffectiveFrom());
  }
}
