package com.chm.myfinances.application.budget;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of {@link BudgetReportQuery}'s budget-vs-actual report (F006 spec's {@code GET
 * /api/budgets/report}): a budgeted category's cap for the queried month (resolved via {@link
 * BudgetCapQuery}, {@code null} if no version is effective yet that far back) and its actual
 * expense total for that same month, across all accounts.
 */
public record BudgetReportLine(UUID categoryId, BigDecimal cap, BigDecimal actual) {}
