package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalInput;
import com.chm.myfinances.domain.goal.GoalStatus;
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
 * Request body for {@code PATCH /api/goals/{id}} (F039 spec): a full replace, so every field is
 * required except the optional notes and target date, plus the {@code status} (also how a goal is
 * archived or restored). The same bounds as {@link CreateGoalRequest}.
 */
public record UpdateGoalRequest(
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
    @NotNull GoalStatus status,
    @NotNull Boolean showOnDashboard) {

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
        showOnDashboard);
  }
}
