package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalAllocationForProductQuery.ProductGoalAllocation;
import java.math.BigDecimal;
import java.util.UUID;

/** One goal an investment product is allocated to (F039 spec), with the share it holds. */
public record ProductGoalAllocationResponse(UUID goalId, String goalName, BigDecimal percentage) {

  public static ProductGoalAllocationResponse from(ProductGoalAllocation allocation) {
    return new ProductGoalAllocationResponse(
        allocation.goalId(), allocation.goalName(), allocation.percentage());
  }
}
