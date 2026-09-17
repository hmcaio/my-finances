package com.chm.myfinances.infrastructure.web.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Request body for {@code POST /api/budgets} (F006 spec): a target category plus its first {@code
 * BudgetVersion}. The {@code categoryId} must resolve to an existing {@code EXPENSE} category and
 * not already have a budget - both checked at the application layer ({@code BudgetService}), since
 * neither can be expressed as a per-field bean validation constraint.
 */
public record CreateBudgetRequest(
    @NotNull UUID categoryId,
    @NotNull @Positive BigDecimal monthlyCap,
    @NotNull YearMonth effectiveFrom) {}
