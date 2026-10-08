package com.chm.myfinances.application.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.allocationplan.AllocationPlanEntry;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanRepository;
import com.chm.myfinances.testsupport.fakes.FakeAllocationPlanVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FiiAllocationQuery} (F026 spec, ADR 0023): actual-by-ticker/segment percentages
 * sum to 100 across FII products only, an unsegmented product groups under "No segment", and
 * planned-by-ticker/segment mirror the current {@code AllocationPlanVersion}.
 */
class FiiAllocationQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSubcategoryRepository subcategoryRepository =
      new FakeInvestmentSubcategoryRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final FakeAllocationPlanRepository planRepository = new FakeAllocationPlanRepository();
  private final FakeAllocationPlanVersionRepository versionRepository =
      new FakeAllocationPlanVersionRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final InvestmentSnapshotFreshnessQuery freshnessQuery =
      new InvestmentSnapshotFreshnessQuery(
          latestSnapshotQuery, transferRepository, holdingRepository);
  private final FiiPortfolioQuery portfolioQuery =
      new FiiPortfolioQuery(
          productRepository,
          subcategoryRepository,
          holdingRepository,
          transferRepository,
          latestSnapshotQuery,
          freshnessQuery,
          clock);
  private final AllocationPlanService allocationPlanService =
      new AllocationPlanService(
          planRepository,
          versionRepository,
          productRepository,
          subcategoryRepository,
          new com.chm.myfinances.testsupport.fakes.FakeIdGenerator());
  private final FiiAllocationQuery query =
      new FiiAllocationQuery(
          portfolioQuery, allocationPlanService, productRepository, segmentRepository, clock);

  private final UUID fiiSubcategoryId =
      subcategoryRepository
          .save(
              InvestmentSubcategory.create(
                  UUID.randomUUID(), UUID.randomUUID(), AllocationPlanService.FII_SUBCATEGORY_NAME))
          .getId();

  private InvestmentProduct fiiProduct(String name, String ticker, UUID segmentId) {
    return productRepository.save(
        InvestmentProductMother.product()
            .withName(name)
            .withInvestmentSubcategoryId(fiiSubcategoryId)
            .withTicker(ticker)
            .withSegmentId(segmentId)
            .build());
  }

  private InvestmentHolding withSnapshot(InvestmentProduct product, String value) {
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), TODAY.minusDays(1), new BigDecimal(value)));
    return holding;
  }

  @Test
  void actualByTickerPercentagesSumTo100AcrossFiiProductsOnly() {
    InvestmentProduct knri = fiiProduct("KNRI11 Fund", "KNRI11", null);
    InvestmentProduct hglg = fiiProduct("HGLG11 Fund", "HGLG11", null);
    withSnapshot(knri, "600.00");
    withSnapshot(hglg, "400.00");
    // A non-FII product with value must not affect the FII-only total.
    InvestmentProduct nonFii =
        productRepository.save(InvestmentProductMother.product().withName("Non FII").build());
    withSnapshot(nonFii, "1000.00");

    List<FiiAllocationRow> rows =
        query.allocation(FiiAllocationBasis.ACTUAL, FiiAllocationGroupBy.TICKER);

    assertThat(rows).hasSize(2);
    BigDecimal sum =
        rows.stream().map(FiiAllocationRow::percentage).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(sum).isEqualByComparingTo("100.00");
    assertThat(
            rows.stream()
                .filter(r -> r.key().equals(knri.getId()))
                .findFirst()
                .orElseThrow()
                .percentage())
        .isEqualByComparingTo("60.00");
  }

  @Test
  void anUnsegmentedProductGroupsUnderNoSegmentInTheActualSegmentChart() {
    UUID segmentId =
        segmentRepository
            .save(InvestmentSegment.create(UUID.randomUUID(), "Shoppings Test"))
            .getId();
    InvestmentProduct segmented = fiiProduct("KNRI11 Fund", "KNRI11", segmentId);
    InvestmentProduct unsegmented = fiiProduct("HGLG11 Fund", "HGLG11", null);
    withSnapshot(segmented, "500.00");
    withSnapshot(unsegmented, "500.00");

    List<FiiAllocationRow> rows =
        query.allocation(FiiAllocationBasis.ACTUAL, FiiAllocationGroupBy.SEGMENT);

    assertThat(rows).hasSize(2);
    assertThat(rows)
        .anySatisfy(
            r -> {
              assertThat(r.key()).isNull();
              assertThat(r.label()).isEqualTo("No segment");
              assertThat(r.percentage()).isEqualByComparingTo("50.00");
            });
  }

  @Test
  void actualAllocationIsEmptyWhenNoFiiProductHasAnyValue() {
    fiiProduct("KNRI11 Fund", "KNRI11", null);

    assertThat(query.allocation(FiiAllocationBasis.ACTUAL, FiiAllocationGroupBy.TICKER)).isEmpty();
  }

  @Test
  void plannedByTickerMirrorsTheCurrentPlanVersion() {
    InvestmentProduct knri = fiiProduct("KNRI11 Fund", "KNRI11", null);
    InvestmentProduct hglg = fiiProduct("HGLG11 Fund", "HGLG11", null);
    allocationPlanService.setAllocation(
        List.of(
            new AllocationPlanEntry(knri.getId(), new BigDecimal("70.00")),
            new AllocationPlanEntry(hglg.getId(), new BigDecimal("30.00"))),
        YearMonth.from(TODAY));

    List<FiiAllocationRow> rows =
        query.allocation(FiiAllocationBasis.PLANNED, FiiAllocationGroupBy.TICKER);

    assertThat(rows).hasSize(2);
    assertThat(
            rows.stream()
                .filter(r -> r.key().equals(knri.getId()))
                .findFirst()
                .orElseThrow()
                .percentage())
        .isEqualByComparingTo("70.00");
  }

  @Test
  void plannedBySegmentSumsCorrectly() {
    UUID segmentId =
        segmentRepository
            .save(InvestmentSegment.create(UUID.randomUUID(), "Shoppings Test"))
            .getId();
    InvestmentProduct knri = fiiProduct("KNRI11 Fund", "KNRI11", segmentId);
    InvestmentProduct hglg = fiiProduct("HGLG11 Fund", "HGLG11", segmentId);
    InvestmentProduct xplg = fiiProduct("XPLG11 Fund", "XPLG11", null);
    allocationPlanService.setAllocation(
        List.of(
            new AllocationPlanEntry(knri.getId(), new BigDecimal("40.00")),
            new AllocationPlanEntry(hglg.getId(), new BigDecimal("30.00")),
            new AllocationPlanEntry(xplg.getId(), new BigDecimal("30.00"))),
        YearMonth.from(TODAY));

    List<FiiAllocationRow> rows =
        query.allocation(FiiAllocationBasis.PLANNED, FiiAllocationGroupBy.SEGMENT);

    assertThat(rows).hasSize(2);
    assertThat(
            rows.stream()
                .filter(r -> segmentId.equals(r.key()))
                .findFirst()
                .orElseThrow()
                .percentage())
        .isEqualByComparingTo("70.00");
    assertThat(rows.stream().filter(r -> r.key() == null).findFirst().orElseThrow().percentage())
        .isEqualByComparingTo("30.00");
  }

  @Test
  void plannedAllocationIsEmptyWhenNoPlanHasEverBeenSet() {
    assertThat(query.allocation(FiiAllocationBasis.PLANNED, FiiAllocationGroupBy.TICKER)).isEmpty();
  }
}
