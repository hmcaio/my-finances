package com.chm.myfinances.application.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Sum of expense transactions per category for a month (F012 spec, PRD S6.8's "monthly spend by
 * category"), across all accounts. Unlike F006's budget report it covers every category that has
 * spending, budgeted or not. Read-only query object, same convention as {@code BudgetReportQuery}:
 * reuses the existing {@link TransactionRepository#findAll} with an unpaged request rather than
 * adding a port method.
 */
@Service
public class MonthlySpendByCategoryQuery {

  private final TransactionRepository transactionRepository;

  public MonthlySpendByCategoryQuery(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  /** One row per category with spending in {@code month}, largest total first. */
  public List<CategorySpend> forMonth(YearMonth month) {
    TransactionFilter filter =
        new TransactionFilter(month.atDay(1), month.atEndOfMonth(), null, null, null);
    Map<UUID, BigDecimal> totals =
        transactionRepository.findAll(filter, Pageable.unpaged()).getContent().stream()
            .filter(t -> t.getType() == CategoryType.EXPENSE)
            .collect(
                Collectors.groupingBy(
                    Transaction::getCategoryId,
                    Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));
    return totals.entrySet().stream()
        .map(e -> new CategorySpend(e.getKey(), e.getValue()))
        .sorted(Comparator.comparing(CategorySpend::total).reversed())
        .toList();
  }
}
