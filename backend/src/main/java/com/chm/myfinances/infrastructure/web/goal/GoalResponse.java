package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a {@link Goal} (F039 spec, ADR 0034). */
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
    boolean showOnDashboard) {

  public static GoalResponse from(Goal goal) {
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
        goal.isShowOnDashboard());
  }
}
