package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.application.networth.NetWorthQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountLedgerActivity;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AnnualSummaryReportGenerator} (F030 spec), written first (ADR 0004): the {@code
 * month} filter's year selects the calendar year, and the single-row CSV carries every {@link
 * AnnualSummary} field.
 */
class AnnualSummaryReportGeneratorTest {

  private static final LocalDate TODAY = LocalDate.of(2027, 1, 1);

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
              new FakeAccountLedgerActivity(transactionRepository, transferRepository),
              holdingRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository)),
          new FakeAccountLedgerActivity(transactionRepository, transferRepository),
          transactionRepository,
          transferRepository,
          holdingRepository,
          snapshotRepository,
          TestClocks.fixedAtStartOf(TODAY));
  private final AnnualSummaryReportGenerator generator =
      new AnnualSummaryReportGenerator(
          new AnnualSummaryQuery(transactionRepository, netWorthQuery),
          TestClocks.fixedAtStartOf(TODAY));

  @Test
  void usesTheMonthFiltersYearAsTheYearSelector() {
    Account checking =
        accountRepository.save(
            AccountMother.checking()
                .withOpeningBalance(new BigDecimal("1000.00"))
                .withOpeningBalanceDate(LocalDate.of(2025, 1, 1))
                .build());
    transactionRepository.save(
        TransactionMother.income()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2026, 5, 1))
            .withAmount(new BigDecimal("200.00"))
            .build());
    // 2025 income - must not count for the 2026 report.
    transactionRepository.save(
        TransactionMother.income()
            .withAccountId(checking.getId())
            .withDate(LocalDate.of(2025, 5, 1))
            .withAmount(new BigDecimal("999.00"))
            .build());

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, null, null, null, null, YearMonth.of(2026, 6), null));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("year")).isEqualTo("2026");
    assertThat(rows.get(0).get("total_income")).isEqualTo("200.00");
    // Net worth is cumulative, not year-scoped: the 2025 income already happened by 2026-01-01,
    // so it's part of the running balance net worth reports at the year's start.
    assertThat(rows.get(0).get("net_worth_start")).isEqualTo("1999.00");
    assertThat(rows.get(0).get("net_worth_end")).isEqualTo("2199.00");
    assertThat(rows.get(0).get("net_worth_delta")).isEqualTo("200.00");
  }
}
