package com.chm.myfinances.application.goal;

import com.chm.myfinances.domain.goal.Goal;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A goal as the list and the detail page show it (F039 spec, ADR 0034): the goal, where it stands
 * ({@link GoalProgress}) and its allocations with the product names, largest share first.
 */
public record GoalOverview(Goal goal, GoalProgress progress, List<AllocationDetail> allocations) {

  /** One allocated product: its id, its name for display, and the share of it given to the goal. */
  public record AllocationDetail(UUID productId, String productName, BigDecimal percentage) {}
}
