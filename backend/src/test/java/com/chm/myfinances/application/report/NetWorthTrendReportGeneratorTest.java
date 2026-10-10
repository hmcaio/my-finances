package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.application.networth.NetWorthGranularity;
import com.chm.myfinances.application.networth.NetWorthQuery;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NetWorthTrendReportGenerator} (F030 spec), written first (ADR 0004): the
 * existing trend series rendered as CSV, honoring the date range and granularity filters.
 */
class NetWorthTrendReportGeneratorTest {

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final NetWorthTrendReportGenerator generator =
      new NetWorthTrendReportGenerator(
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
              TestClocks.fixedAtStartOf(LocalDate.of(2026, 6, 15))),
          TestClocks.fixedAtStartOf(LocalDate.of(2026, 6, 15)));

  @Test
  void rendersOneRowPerMonthWhenGranularityIsMonth() {
    accountRepository.save(
        AccountMother.checking()
            .withOpeningBalance(new BigDecimal("1000.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build());

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                null,
                null,
                null,
                null,
                null,
                NetWorthGranularity.MONTH));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(3);
    assertThat(rows).allSatisfy(r -> assertThat(r.get("net_worth")).isEqualTo("1000.00"));
  }
}
