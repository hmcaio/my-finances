package com.chm.myfinances.application.transaction;

import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Sum of expense transactions per category for a month (F012 spec, PRD S6.8's "monthly spend by
 * category"), across all accounts. Unlike F006's budget report it covers every category that has
 * spending, budgeted or not. Read-only query object, aggregated at the persistence layer by {@link
 * TransactionRepository#sumExpenseAmountByCategoryForDateRange} instead of loading every matching
 * transaction into memory to group in Java.
 */
@Service
public class MonthlySpendByCategoryQuery {

  private final TransactionRepository transactionRepository;

  public MonthlySpendByCategoryQuery(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /** One row per category with spending in {@code month}, largest total first. */
  public List<CategorySpend> forMonth(YearMonth month) {
    Map<UUID, BigDecimal> totals =
        transactionRepository.sumExpenseAmountByCategoryForDateRange(
            month.atDay(1), month.atEndOfMonth());
    return totals.entrySet().stream()
        .map(e -> new CategorySpend(e.getKey(), e.getValue()))
        .sorted(Comparator.comparing(CategorySpend::total).reversed())
        .toList();
  }
}
