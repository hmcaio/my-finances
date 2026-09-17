package com.chm.myfinances.infrastructure.web.budget;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Request body for {@code PATCH /api/budgets/{id}/cap} (F006 spec): creates a new {@code
 * BudgetVersion} effective from {@code effectiveFrom}, or replaces the existing version for that
 * exact month if one is already there ({@code BudgetService.setCap}'s same-month rule).
 */
public record UpdateBudgetCapRequest(
    @NotNull @Positive BigDecimal monthlyCap, @NotNull YearMonth effectiveFrom) {}
