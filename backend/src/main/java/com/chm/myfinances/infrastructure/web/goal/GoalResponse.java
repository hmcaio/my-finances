package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalOverview;
import com.chm.myfinances.application.goal.GoalProgress;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API representation of a {@link Goal} as the list, the detail and every write return it (F039
 * spec, ADR 0034): the stored fields, where it stands now ({@code allocatedValue}, {@code
 * progressPercent} which may exceed 100, the automatic {@code targetReached} indicator, the base
 * scenario's {@code projectedShortfall} for a dated goal or {@code monthsToTarget} for a dateless
 * one) and its allocations, largest share first. Amounts are in today's money.
 */
public record GoalResponse(
    UUID id,
    String name,
    String notes,
    BigDecimal targetAmount,
    LocalDate targetDate,
    BigDecimal plannedMonthlyContribution,
    BigDecimal returnPessimistic,
    BigDecimal returnBase,
    BigDecimal returnOptimistic,
    GoalStatus status,
    boolean showOnDashboard,
    BigDecimal allocatedValue,
    BigDecimal progressPercent,
    boolean targetReached,
    BigDecimal projectedShortfall,
    Integer monthsToTarget,
    List<AllocationResponse> allocations) {

  /** One allocated product, with its name for display. */
  public record AllocationResponse(UUID productId, String productName, BigDecimal percentage) {

    static AllocationResponse from(GoalOverview.AllocationDetail detail) {
      return new AllocationResponse(detail.productId(), detail.productName(), detail.percentage());
    }
  }

  public static GoalResponse from(GoalOverview overview) {
    Goal goal = overview.goal();
    GoalProgress progress = overview.progress();
    return new GoalResponse(
        goal.getId(),
        goal.getName(),
        goal.getNotes(),
        goal.getTargetAmount(),
        goal.getTargetDate(),
        goal.getPlannedMonthlyContribution(),
        goal.getReturnPessimistic(),
        goal.getReturnBase(),
        goal.getReturnOptimistic(),
        goal.getStatus(),
        goal.isShowOnDashboard(),
        progress.allocatedValue(),
        progress.progressPercent(),
        progress.targetReached(),
        progress.projectedShortfall(),
        progress.monthsToTarget(),
        overview.allocations().stream().map(AllocationResponse::from).toList());
  }
}
