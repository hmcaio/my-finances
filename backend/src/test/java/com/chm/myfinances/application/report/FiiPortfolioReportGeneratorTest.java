package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.investmentreport.FiiPortfolioQuery;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FiiPortfolioReportGenerator} (F030 spec), written first (ADR 0004): the FII
 * portfolio CSV/PDF as of a selectable month, resolved with the F009/F010 month-end-except-
 * current-month-at-today convention.
 */
class FiiPortfolioReportGeneratorTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final FiiPortfolioQuery portfolioQuery =
      new FiiPortfolioQuery(
          productRepository,
          subcategoryRepository,
          holdingRepository,
          tradeLineRepository,
          latestSnapshotQuery,
          new InvestmentSnapshotFreshnessQuery(
              latestSnapshotQuery, tradeLineRepository, holdingRepository),
          clock);
  private final FiiPortfolioReportGenerator generator =
      new FiiPortfolioReportGenerator(portfolioQuery, segmentRepository, clock);

  private final UUID fiiSubcategoryId =
      subcategoryRepository
          .save(
              InvestmentSubcategory.create(
                  UUID.randomUUID(), UUID.randomUUID(), AllocationPlanService.FII_SUBCATEGORY_NAME))
          .getId();

  @Test
  void rendersCsvAndPdfForTheSelectedMonthWithSegmentNamesResolved() {
    UUID segmentId =
        segmentRepository
            .save(InvestmentSegment.create(UUID.randomUUID(), "Shoppings R Test"))
            .getId();
    InvestmentProduct product =
        productRepository.save(
            InvestmentProductMother.product()
                .withName("Kinea R Test")
                .withTicker("KNRI11")
                .withInvestmentSubcategoryId(fiiSubcategoryId)
                .withSegmentId(segmentId)
                .build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 5, 31),
            new BigDecimal("1000.00")));

    List<GeneratedFile> files =
        generator.generate(
            new ReportFilter(null, null, null, null, null, null, YearMonth.of(2026, 5), null));

    assertThat(files).hasSize(2);
    assertThat(files.get(0).format()).isEqualTo(ReportFormat.CSV);
    assertThat(files.get(1).format()).isEqualTo(ReportFormat.PDF);
    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("ticker")).isEqualTo("KNRI11");
    assertThat(rows.get(0).get("segment_name")).isEqualTo("Shoppings R Test");
    assertThat(rows.get(0).get("current_value")).isEqualTo("1000.00");
    assertThat(files.get(1).content()).isNotEmpty();
  }

  @Test
  void defaultsToTheCurrentMonthEvaluatedAtTodayWhenNoMonthIsGiven() {
    InvestmentProduct product =
        productRepository.save(
            InvestmentProductMother.product()
                .withName("Kinea R Test 2")
                .withTicker("KNRI11b")
                .withInvestmentSubcategoryId(fiiSubcategoryId)
                .build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    // Dated after TODAY - must not count, since the current month resolves "as of today".
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), TODAY.plusDays(1), new BigDecimal("999.00")));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), TODAY, new BigDecimal("500.00")));

    List<GeneratedFile> files = generator.generate(ReportFilter.none());

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows.get(0).get("current_value")).isEqualTo("500.00");
  }
}
