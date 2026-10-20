package com.chm.myfinances.infrastructure.web.allocationplan;

import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import java.math.BigDecimal;
import java.util.UUID;

/** API representation of an {@link AllocationPlanEntry} (F026 spec). */
public record AllocationPlanEntryResponse(UUID investmentProductId, BigDecimal targetPercentage) {

  public static AllocationPlanEntryResponse from(AllocationPlanEntry entry) {
    return new AllocationPlanEntryResponse(entry.investmentProductId(), entry.targetPercentage());
  }
}
