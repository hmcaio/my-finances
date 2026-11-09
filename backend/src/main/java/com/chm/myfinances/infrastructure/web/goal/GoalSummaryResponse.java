package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalProgress;
import com.chm.myfinances.application.goal.GoalSummary;
import com.chm.myfinances.domain.goal.Goal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One goal on the dashboard card of {@code GET /api/goals/dashboard} (F039 spec, ADR 0034), from
 * the base scenario. {@code projectedShortfall} {@code <= 0} means on track; it is set for a dated
 * goal, while {@code monthsToTarget} is set (or null for "not within 50 years") for a dateless one.
 */
public record GoalSummaryResponse(
    UUID id,
    String name,
    BigDecimal targetAmount,
    LocalDate targetDate,
    BigDecimal allocatedValue,
    BigDecimal progressPercent,
    boolean targetReached,
    BigDecimal projectedShortfall,
    Integer monthsToTarget) {

  public static GoalSummaryResponse from(GoalSummary summary) {
    Goal goal = summary.goal();
    GoalProgress progress = summary.progress();
    return new GoalSummaryResponse(
        goal.getId(),
        goal.getName(),
        goal.getTargetAmount(),
        goal.getTargetDate(),
        progress.allocatedValue(),
        progress.progressPercent(),
        progress.targetReached(),
        progress.projectedShortfall(),
        progress.monthsToTarget());
  }
}
