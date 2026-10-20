package com.chm.myfinances.infrastructure.web.recurringtemplate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Request body for {@code PATCH /api/recurring-templates/{id}/cap} (F007 spec): creates a new
 * {@code RecurringTemplateVersion} effective from {@code effectiveFrom}, or replaces the existing
 * version for that exact month if one is already there ({@code RecurringTemplateService.setCap}'s
 * same-month rule, F006 {@code BudgetService.setCap} precedent).
 */
public record UpdateRecurringTemplateCapRequest(
    @NotNull @Positive BigDecimal amount,
    @Min(1) @Max(31) int dayOfMonth,
    @NotNull YearMonth effectiveFrom) {}
