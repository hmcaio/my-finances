package com.chm.myfinances.application.budget;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes budget-vs-actual for every budgeted category for a given month (F006 spec's {@code GET
 * /api/budgets/report?month=YYYY-MM}, PRD S5.6): {@code cap} is whichever {@link BudgetVersion}
 * {@link BudgetCapQuery} resolves as effective for that month, {@code actual} is summed at the
 * persistence layer by F004's {@link TransactionRepository#sumAmountByCategoryAndDateRange} across
 * all accounts for that category/month - one aggregate query per budget rather than loading every
 * matching transaction into memory. Same application-layer "query object for a computed-not-stored
 * value" convention as F003's {@code AccountBalanceQuery} and this feature's own {@code
 * BudgetCapQuery}.
 */
@Service
public class BudgetReportQuery {

  private final BudgetRepository budgetRepository;
  private final BudgetCapQuery budgetCapQuery;
  private final TransactionRepository transactionRepository;

  public BudgetReportQuery(
      BudgetRepository budgetRepository,
      BudgetCapQuery budgetCapQuery,
      TransactionRepository transactionRepository) {
    this.budgetRepository = budgetRepository;
    this.budgetCapQuery = budgetCapQuery;
    this.transactionRepository = transactionRepository;
  }

  public List<BudgetReportLine> forMonth(YearMonth month) {
    return budgetRepository.findAll().stream()
        .map(budget -> toReportLine(budget, month))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * Empty when the budget is stopped as of {@code month} (its effective version is a tombstone).
   */
  private Optional<BudgetReportLine> toReportLine(Budget budget, YearMonth month) {
    Optional<BudgetVersion> effective = budgetCapQuery.effectiveCap(budget.getId(), month);
    if (effective.isPresent() && effective.get().isTombstone()) {
      return Optional.empty();
    }
    BigDecimal cap = effective.map(BudgetVersion::getMonthlyCap).orElse(null);
    BigDecimal actual = sumActualExpenses(budget.getCategoryId(), month);
    return Optional.of(new BudgetReportLine(budget.getCategoryId(), cap, actual));
  }

  /**
   * Sums every transaction in {@code categoryId} within {@code month}, across all accounts (PRD
   * S5.6: "actual for a given month = sum of expense transactions in that category within the
   * month, across all accounts").
   */
  private BigDecimal sumActualExpenses(UUID categoryId, YearMonth month) {
    LocalDate from = month.atDay(1);
    LocalDate to = month.atEndOfMonth();
    return transactionRepository.sumAmountByCategoryAndDateRange(categoryId, from, to);
  }
}
