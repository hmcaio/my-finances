package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.domain.goal.GoalAllocation;
import java.math.BigDecimal;
import java.util.UUID;

/** API representation of a {@link GoalAllocation} (F039 spec, ADR 0034). */
public record GoalAllocationResponse(UUID productId, BigDecimal percentage) {

  public static GoalAllocationResponse from(GoalAllocation allocation) {
    return new GoalAllocationResponse(allocation.getProductId(), allocation.getPercentage());
  }
}
