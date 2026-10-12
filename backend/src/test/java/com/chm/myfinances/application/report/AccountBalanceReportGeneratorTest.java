package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AccountBalanceReportGenerator} (F030 spec), written first (ADR 0004): every
 * account (open and closed) is included, the as-of date falls back to the latest investment
 * snapshot at or before it (the as-of fallback {@link AccountBalanceQuery} already provides), and
 * the account filter narrows to one account.
 */
class AccountBalanceReportGeneratorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final AccountBalanceReportGenerator generator =
      new AccountBalanceReportGenerator(
          accountRepository,
          new AccountBalanceQuery(
              transactionRepository,
              transferRepository,
              holdingRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository)),
          TestClocks.fixedAtStartOf(TODAY));

  @Test
  void includesEveryAccountOpenAndClosedWithABalanceAsOfToday() {
    accountRepository.save(
        AccountMother.checking()
            .withName("Checking R Test")
            .withOpeningBalance(new BigDecimal("500.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build());
    Account closed =
        AccountMother.savings()
            .withName("Old Savings R Test")
            .withOpeningBalance(new BigDecimal("200.00"))
            .withOpeningBalanceDate(LocalDate.of(2020, 1, 1))
            .build();
    closed.close(LocalDate.of(2025, 1, 1));
    accountRepository.save(closed);

    List<GeneratedFile> files = generator.generate(ReportFilter.none());

    assertThat(files).hasSize(1);
    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("account_name")).isEqualTo("Checking R Test");
              assertThat(r.get("balance")).isEqualTo("500.00");
              assertThat(r.get("closed_date")).isEmpty();
            });
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.get("account_name")).isEqualTo("Old Savings R Test");
              assertThat(r.get("balance")).isEqualTo("200.00");
              assertThat(r.get("closed_date")).isEqualTo("2025-01-01");
            });
  }

  @Test
  void anAsOfDateWithNoExactSnapshotFallsBackToTheLatestOneBeforeIt() {
    Account broker =
        accountRepository.save(AccountMother.investment().withName("Broker R Test").build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withAccountId(broker.getId()).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 3, 31),
            new BigDecimal("1000.00")));
    // No snapshot exists exactly on 2026-04-30; the generator's as-of date falls back to March's.

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, LocalDate.of(2026, 4, 30), null, null, null, null, null));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("balance")).isEqualTo("1000.00");
  }

  @Test
  void theAccountFilterNarrowsToOneAccount() {
    Account checking =
        accountRepository.save(
            AccountMother.checking()
                .withName("Checking R Test 2")
                .withOpeningBalance(new BigDecimal("10.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
                .build());
    accountRepository.save(
        AccountMother.savings()
            .withName("Savings R Test 2")
            .withOpeningBalance(new BigDecimal("20.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build());

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, null, checking.getId(), null, null, null, null));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("account_name")).isEqualTo("Checking R Test 2");
  }

  @Test
  void defaultsAsOfToToday() {
    accountRepository.save(
        AccountMother.checking()
            .withName("Checking R Test 3")
            .withOpeningBalance(new BigDecimal("5.00"))
            .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
            .build());

    List<GeneratedFile> files = generator.generate(ReportFilter.none());

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows.get(0).get("balance")).isEqualTo("5.00");
  }
}
