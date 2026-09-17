package com.chm.myfinances.infrastructure.web.budget;

import com.chm.myfinances.application.budget.BudgetReportLine;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of one {@link BudgetReportLine} (F006 spec's {@code GET
 * /api/budgets/report?month=YYYY-MM}). {@code cap} is {@code null} when no {@code BudgetVersion} is
 * effective yet for the queried month.
 */
public record BudgetReportLineResponse(UUID categoryId, BigDecimal cap, BigDecimal actual) {

  public static BudgetReportLineResponse from(BudgetReportLine line) {
    return new BudgetReportLineResponse(line.categoryId(), line.cap(), line.actual());
  }
}
