package com.chm.myfinances.infrastructure.web.budget;

import jakarta.validation.constraints.NotNull;
import java.time.YearMonth;

/**
 * Request body for {@code POST /api/budgets/{id}/stop} (issue #61): stops budgeting from {@code
 * effectiveFrom} onward by storing a tombstone {@code BudgetVersion} ({@code BudgetService.stop}).
 * Earlier months keep their cap.
 */
public record StopBudgetRequest(@NotNull YearMonth effectiveFrom) {}
