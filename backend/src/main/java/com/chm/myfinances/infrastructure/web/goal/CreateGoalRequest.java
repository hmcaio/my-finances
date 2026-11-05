package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalInput;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/goals} (F039 spec, ADR 0034). Money is up to two decimals (the
 * {@code numeric(19,2)} columns); the contribution is {@code >= 0} (the one documented exception to
 * the positive-amount rule); each return is a percentage from {@code -50} to {@code 100} with two
 * decimals. {@code showOnDashboard} defaults to {@code true}. That the target date is in the future
 * is checked in the service (it needs the clock).
 */
public record CreateGoalRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String notes,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal targetAmount,
    LocalDate targetDate,
    @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2)
        BigDecimal plannedMonthlyContribution,
    @NotNull @DecimalMin("-50") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal returnPessimistic,
    @NotNull @DecimalMin("-50") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal returnBase,
    @NotNull @DecimalMin("-50") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal returnOptimistic,
    Boolean showOnDashboard) {

  GoalInput toInput() {
    return new GoalInput(
        name,
        notes,
        targetAmount,
        targetDate,
        plannedMonthlyContribution,
        returnPessimistic,
        returnBase,
        returnOptimistic,
        showOnDashboard == null || showOnDashboard);
  }
}
