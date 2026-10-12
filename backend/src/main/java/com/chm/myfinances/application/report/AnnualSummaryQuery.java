package com.chm.myfinances.application.report;

import com.chm.myfinances.application.networth.NetWorthPoint;
import com.chm.myfinances.application.networth.NetWorthQuery;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The Annual Summary report's figures (F030 spec): total income/expense for a calendar year, summed
 * in Java over {@link TransactionRepository#findAll} - consistent with this app's small local
 * dataset, no new repository aggregate method - plus net worth at the year's first and last day via
 * the existing {@link NetWorthQuery#asOf}. Deliberately not a repository-level aggregate; a local,
 * single-user app never has enough rows for that to matter (same reasoning backend {@code
 * CLAUDE.md} gives for in-memory filtering elsewhere).
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

    BigDecimal income = BigDecimal.ZERO.setScale(2);
    BigDecimal expense = BigDecimal.ZERO.setScale(2);
    for (Transaction transaction :
        transactionRepository
            .findAll(new TransactionFilter(start, end, null, null, null), Pageable.unpaged())
            .getContent()) {
      if (transaction.getType() == CategoryType.INCOME) {
        income = income.add(transaction.getAmount());
      } else {
        expense = expense.add(transaction.getAmount());
      }
    }

    NetWorthPoint atStart = netWorthQuery.asOf(start);
    NetWorthPoint atEnd = netWorthQuery.asOf(end);
    BigDecimal delta = atEnd.netWorth().subtract(atStart.netWorth());

    return new AnnualSummary(
        year.getValue(), income, expense, atStart.netWorth(), atEnd.netWorth(), delta);
  }
}
