package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The editable fields of a goal, as the controller hands them to {@link GoalService#create} and
 * {@link GoalService#edit} (F039). Whether they are valid is the domain's call ({@code Goal}), plus
 * the service's target-date check.
 */
public record GoalInput(
    String name,
    String notes,
    BigDecimal targetAmount,
    LocalDate targetDate,
    BigDecimal plannedMonthlyContribution,
    BigDecimal returnPessimistic,
    BigDecimal returnBase,
    BigDecimal returnOptimistic,
    boolean showOnDashboard) {}
