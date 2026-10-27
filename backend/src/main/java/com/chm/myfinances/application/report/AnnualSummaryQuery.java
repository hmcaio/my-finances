package com.chm.myfinances.application.report;

import com.chm.myfinances.application.networth.NetWorthPoint;
import com.chm.myfinances.application.networth.NetWorthQuery;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * The Annual Summary report's figures (F030 spec): total income/expense for a calendar year, from
 * {@link TransactionRepository#sumAmountByType} (the aggregate the Ledger summary already uses; it
 * was summed in Java over every row of the year until issue #189), plus net worth at the year's
 * first and last day via the existing {@link NetWorthQuery#asOf}.
 */
@Service
public class AnnualSummaryQuery {

  private final TransactionRepository transactionRepository;
  private final NetWorthQuery netWorthQuery;

  public AnnualSummaryQuery(
      TransactionRepository transactionRepository, NetWorthQuery netWorthQuery) {
    this.transactionRepository = transactionRepository;
    this.netWorthQuery = netWorthQuery;
  }

  public AnnualSummary summarize(Year year) {
    LocalDate start = year.atDay(1);
    LocalDate end = year.atMonth(12).atEndOfMonth();

    Map<CategoryType, BigDecimal> totals =
        transactionRepository.sumAmountByType(new TransactionFilter(start, end, null, null, null));
    BigDecimal income =
        BigDecimal.ZERO.setScale(2).add(totals.getOrDefault(CategoryType.INCOME, BigDecimal.ZERO));
    BigDecimal expense =
        BigDecimal.ZERO.setScale(2).add(totals.getOrDefault(CategoryType.EXPENSE, BigDecimal.ZERO));

    NetWorthPoint atStart = netWorthQuery.asOf(start);
    NetWorthPoint atEnd = netWorthQuery.asOf(end);
    BigDecimal delta = atEnd.netWorth().subtract(atStart.netWorth());

    return new AnnualSummary(
        year.getValue(), income, expense, atStart.netWorth(), atEnd.netWorth(), delta);
  }
}
