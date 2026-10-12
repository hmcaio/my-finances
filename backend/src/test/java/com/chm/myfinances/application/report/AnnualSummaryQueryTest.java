package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.application.networth.NetWorthQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AnnualSummaryQuery} (F030 spec), written first (ADR 0004): income/expense totals
 * summed only within the calendar year, and net worth at the year's first/last day with the delta.
 */
class AnnualSummaryQueryTest {

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final NetWorthQuery netWorthQuery =
      new NetWorthQuery(
          accountRepository,
          new AccountBalanceQuery(
              transactionRepository,
              transferRepository,
              holdingRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository)),
          transactionRepository,
          transferRepository,
          snapshotRepository,
          TestClocks.fixedAtStartOf(LocalDate.of(2027, 1, 1)));
  private final AnnualSummaryQuery query =
      new AnnualSummaryQuery(transactionRepository, netWorthQuery);

  private Account save(AccountMother mother) {
    return accountRepository.save(mother.build());
  }

  @Test
  void sumsIncomeAndExpenseOnlyWithinTheCalendarYear() {
    Account checking =
        save(
            AccountMother.checking()
                .withOpeningBalance(bd("0"))
                .withOpeningBalanceDate(LocalDate.of(2020, 1, 1)));
    transactionRepository.save(
        TransactionMother.income()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2026, 3, 1))
            .withAmount(bd("1000.00"))
            .build());
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2026, 6, 1))
            .withAmount(bd("400.00"))
            .build());
    // Outside the year - must not count.
    transactionRepository.save(
        TransactionMother.income()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2025, 12, 31))
            .withAmount(bd("9999.00"))
            .build());
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2027, 1, 1))
            .withAmount(bd("9999.00"))
            .build());

    AnnualSummary summary = query.summarize(Year.of(2026));

    assertThat(summary.year()).isEqualTo(2026);
    assertThat(summary.totalIncome()).isEqualByComparingTo("1000.00");
    assertThat(summary.totalExpense()).isEqualByComparingTo("400.00");
  }

  @Test
  void reportsNetWorthAtYearStartAndEndWithTheDelta() {
    save(
        AccountMother.checking()
            .withOpeningBalance(bd("500.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1)));
    Account checking2 =
        save(
            AccountMother.checking()
                .withName("Checking 2")
                .withOpeningBalance(bd("500.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1)));
    transactionRepository.save(
        TransactionMother.income()
            .withAccountId(checking2.getId())
            .withDate(LocalDate.of(2026, 6, 1))
            .withAmount(bd("300.00"))
            .build());

    AnnualSummary summary = query.summarize(Year.of(2026));

    assertThat(summary.netWorthStart()).isEqualByComparingTo("1000.00");
    assertThat(summary.netWorthEnd()).isEqualByComparingTo("1300.00");
    assertThat(summary.netWorthDelta()).isEqualByComparingTo("300.00");
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }
}
