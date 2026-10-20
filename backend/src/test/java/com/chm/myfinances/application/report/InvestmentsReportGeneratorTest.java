package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentsReportGenerator} (F030 spec), written first (ADR 0004): the snapshot
 * section's as-of fallback, and the activity section's date-range filter over trade lines.
 */
class InvestmentsReportGeneratorTest {

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final InvestmentsReportGenerator generator =
      new InvestmentsReportGenerator(
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          holdingRepository,
          productRepository,
          accountRepository,
          tradeLineRepository,
          TestClocks.fixedAtStartOf(LocalDate.of(2026, 6, 15)));

  @Test
  void snapshotSectionFallsBackToTheLatestSnapshotAtOrBeforeAsOf() {
    Account broker =
        accountRepository.save(AccountMother.investment().withName("Broker R Test").build());
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName("CDB R Test").build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(broker.getId())
                .build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 4, 30),
            new BigDecimal("1000.00")));

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, LocalDate.of(2026, 5, 31), null, null, null, null, null));

    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);
    assertThat(sections.get(0)).hasSize(1);
    assertThat(sections.get(0).get(0).get("product_name")).isEqualTo("CDB R Test");
    assertThat(sections.get(0).get(0).get("balance")).isEqualTo("1000.00");
    assertThat(sections.get(0).get(0).get("snapshot_date")).isEqualTo("2026-04-30");
  }

  @Test
  void activitySectionIsFilteredToTheDateRange() {
    Account checking =
        accountRepository.save(AccountMother.checking().withName("Checking R Test").build());
    Account broker =
        accountRepository.save(AccountMother.investment().withName("Broker R Test 2").build());
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName("PETR4 R Test").build());
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            checking.getId(),
            broker.getId(),
            product.getId(),
            TradeSide.BUY,
            BigDecimal.TEN,
            BigDecimal.ONE,
            null));
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 6, 1),
            checking.getId(),
            broker.getId(),
            product.getId(),
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.TEN,
            null));

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31),
                null,
                null,
                null,
                null,
                null,
                null));

    List<List<Map<String, String>>> sections =
        CsvTestSupport.parseSections(files.get(0).content(), 2);
    assertThat(sections.get(1)).hasSize(1);
    assertThat(sections.get(1).get(0).get("date")).isEqualTo("2026-06-01");
    assertThat(sections.get(1).get(0).get("product_name")).isEqualTo("PETR4 R Test");
    assertThat(sections.get(1).get(0).get("from_account_name")).isEqualTo("Checking R Test");
    assertThat(sections.get(1).get(0).get("to_account_name")).isEqualTo("Broker R Test 2");
  }
}
