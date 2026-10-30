package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentOpeningPositionRepository;
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
  private final FakeInvestmentOpeningPositionRepository positionRepository =
      new FakeInvestmentOpeningPositionRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final InvestmentsReportGenerator generator =
      new InvestmentsReportGenerator(
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          holdingRepository,
          productRepository,
          accountRepository,
          tradeLineRepository,
          positionRepository,
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

  // --- F038: the OPENING POSITIONS section ---------------------------------------------------

  private InvestmentHolding holdingOf(String productName, String accountName) {
    Account broker =
        accountRepository.save(AccountMother.investment().withName(accountName).build());
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName(productName).build());
    return holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(product.getId())
            .withAccountId(broker.getId())
            .build());
  }

  private void position(InvestmentHolding holding, LocalDate asOf, String qty, String price) {
    positionRepository.save(
        InvestmentOpeningPosition.create(
            UUID.randomUUID(), holding.getId(), asOf, new BigDecimal(qty), new BigDecimal(price)));
  }

  private List<Map<String, String>> openingPositionsSection(ReportFilter filter) {
    List<GeneratedFile> files = generator.generate(filter);
    return CsvTestSupport.parseSections(files.get(0).content(), 3).get(2);
  }

  @Test
  void openingPositionsSectionListsTheColumnsOfEachPosition() {
    InvestmentHolding holding = holdingOf("FII Pos R Test", "Broker Pos R Test");
    position(holding, LocalDate.of(2026, 6, 1), "40", "150.50");
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 6, 1),
            new BigDecimal("6400.00")));

    List<Map<String, String>> rows =
        openingPositionsSection(new ReportFilter(null, null, null, null, null, null, null, null));

    assertThat(rows).hasSize(1);
    Map<String, String> row = rows.get(0);
    assertThat(row.get("product_name")).isEqualTo("FII Pos R Test");
    assertThat(row.get("account_name")).isEqualTo("Broker Pos R Test");
    assertThat(row.get("as_of_date")).isEqualTo("2026-06-01");
    assertThat(row.get("quantity")).startsWith("40");
    assertThat(row.get("unit_price")).startsWith("150.5");
    assertThat(row.get("current_value")).isEqualTo("6400.00");
  }

  @Test
  void openingPositionsCurrentValueIsEmptyWithoutASnapshotDatedExactlyAsOfDate() {
    InvestmentHolding noSnapshot = holdingOf("FII NoSnap R Test", "Broker NoSnap R Test");
    position(noSnapshot, LocalDate.of(2026, 6, 1), "10", "10");
    InvestmentHolding otherDay = holdingOf("FII OtherDay R Test", "Broker OtherDay R Test");
    position(otherDay, LocalDate.of(2026, 6, 1), "10", "10");
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            otherDay.getId(),
            LocalDate.of(2026, 5, 31),
            new BigDecimal("99.00")));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            otherDay.getId(),
            LocalDate.of(2026, 6, 2),
            new BigDecimal("98.00")));

    List<Map<String, String>> rows =
        openingPositionsSection(new ReportFilter(null, null, null, null, null, null, null, null));

    assertThat(rows).hasSize(2);
    assertThat(rows).allSatisfy(r -> assertThat(r.get("current_value")).isEmpty());
  }

  @Test
  void openingPositionsAreFilteredByDateFromAndDateToOnAsOfDate() {
    InvestmentHolding early = holdingOf("FII Early R Test", "Broker Early R Test");
    position(early, LocalDate.of(2026, 1, 15), "1", "1");
    InvestmentHolding inRange = holdingOf("FII InRange R Test", "Broker InRange R Test");
    position(inRange, LocalDate.of(2026, 3, 1), "1", "1");
    InvestmentHolding late = holdingOf("FII Late R Test", "Broker Late R Test");
    position(late, LocalDate.of(2026, 5, 20), "1", "1");

    List<Map<String, String>> rows =
        openingPositionsSection(
            new ReportFilter(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 1),
                null,
                null,
                null,
                null,
                null,
                null));

    assertThat(rows).extracting(r -> r.get("product_name")).containsExactly("FII InRange R Test");
  }

  @Test
  void openingPositionsSectionIsEmptyWhenThereAreNone() {
    assertThat(
            openingPositionsSection(
                new ReportFilter(null, null, null, null, null, null, null, null)))
        .isEmpty();
  }
}
