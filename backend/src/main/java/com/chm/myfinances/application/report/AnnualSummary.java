package com.chm.myfinances.application.report;

import java.math.BigDecimal;

/**
 * One calendar year's headline figures (F030 spec's Annual Summary report): total income, total
 * expense, and net worth at the start and end of the year with the delta. Deliberately high-level -
 * no per-category breakdown, so it doesn't duplicate the Budgets &amp; spending report's job.
 */
public record AnnualSummary(
    int year,
    BigDecimal totalIncome,
    BigDecimal totalExpense,
    BigDecimal netWorthStart,
    BigDecimal netWorthEnd,
    BigDecimal netWorthDelta) {}
