package com.chm.myfinances.application.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.allocationplan.AllocationPlanService;
import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FiiPortfolioQuery} (F026 spec, ADR 0023; reworked onto {@code
 * TransferTradeLine}s by F027/ADR 0024): per FII product, cotas held/amount contributed as a
 * running total across every holding/account (signed by each line's {@code side}), current
 * value/needsSnapshot rolled up from holdings, and the status filter.
 */
class FiiPortfolioQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
  private static final UUID CHECKING_ID = UUID.randomUUID();

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
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final InvestmentSnapshotFreshnessQuery freshnessQuery =
      new InvestmentSnapshotFreshnessQuery(
          latestSnapshotQuery, tradeLineRepository, holdingRepository);
  private final FiiPortfolioQuery query =
      new FiiPortfolioQuery(
          productRepository,
          subcategoryRepository,
          holdingRepository,
          tradeLineRepository,
          latestSnapshotQuery,
          freshnessQuery,
          clock);

  private final UUID fiiSubcategoryId =
      subcategoryRepository
          .save(
              InvestmentSubcategory.create(
                  UUID.randomUUID(), UUID.randomUUID(), AllocationPlanService.FII_SUBCATEGORY_NAME))
          .getId();

  private InvestmentProduct fiiProduct(String name, String ticker) {
    return productRepository.save(
        InvestmentProductMother.product()
            .withName(name)
            .withInvestmentSubcategoryId(fiiSubcategoryId)
            .withTicker(ticker)
            .build());
  }

  @Test
  void nonFiiProductsAreExcluded() {
    productRepository.save(InvestmentProductMother.product().withName("Not FII Test").build());

    assertThat(query.portfolio(InvestmentProductStatus.ALL, TODAY)).isEmpty();
  }

  @Test
  void cotasHeldAndAmountContributedMatchHandComputedSumsAcrossTwoHoldings() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    UUID brokerAId = UUID.randomUUID();
    UUID brokerBId = UUID.randomUUID();
    InvestmentHolding holdingA =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(brokerAId)
                .build());
    InvestmentHolding holdingB =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(brokerBId)
                .build());

    buy(product, brokerAId, "100.00", "10");
    buy(product, brokerBId, "100.00", "5");

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL, TODAY);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).cotasHeld()).isEqualByComparingTo("15");
    assertThat(rows.get(0).amountContributed()).isEqualByComparingTo("1500.00");
    assertThat(holdingA).isNotNull();
    assertThat(holdingB).isNotNull();
  }

  @Test
  void sellingTheEntirePositionDropsCotasHeldToZeroWithoutAffectingOtherProducts() {
    InvestmentProduct knri = fiiProduct("KNRI11 Fund", "KNRI11");
    InvestmentProduct hglg = fiiProduct("HGLG11 Fund", "HGLG11");
    UUID brokerId = UUID.randomUUID();
    holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(knri.getId())
            .withAccountId(brokerId)
            .build());
    holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(hglg.getId())
            .withAccountId(brokerId)
            .build());

    buy(knri, brokerId, "100.00", "10");
    sell(knri, brokerId, "105.00", "10");
    buy(hglg, brokerId, "100.00", "2");

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL, TODAY);

    FiiPortfolioRow knriRow =
        rows.stream().filter(r -> r.productId().equals(knri.getId())).findFirst().orElseThrow();
    FiiPortfolioRow hglgRow =
        rows.stream().filter(r -> r.productId().equals(hglg.getId())).findFirst().orElseThrow();
    assertThat(knriRow.cotasHeld()).isEqualByComparingTo("0");
    assertThat(hglgRow.cotasHeld()).isEqualByComparingTo("2");
  }

  @Test
  void currentValueAndNeedsSnapshotMatchExistingHoldingLevelComputationsRolledUp() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    UUID brokerAId = UUID.randomUUID();
    UUID brokerBId = UUID.randomUUID();
    InvestmentHolding holdingA =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(brokerAId)
                .build());
    InvestmentHolding holdingB =
        holdingRepository.save(
            InvestmentHoldingMother.holding()
                .withProductId(product.getId())
                .withAccountId(brokerBId)
                .build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holdingA.getId(),
            LocalDate.of(2026, 5, 1),
            new BigDecimal("1000.00")));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holdingB.getId(),
            LocalDate.of(2026, 5, 1),
            new BigDecimal("500.00")));
    buy(product, brokerAId, "100.00", "1");
    // A trade after the last snapshot makes holdingA stale.
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 5, 2),
            CHECKING_ID,
            brokerAId,
            product.getId(),
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.ONE,
            null));

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL, TODAY);

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).currentValue()).isEqualByComparingTo("1500.00");
    assertThat(rows.get(0).needsSnapshot()).isTrue();
  }

  @Test
  void statusFilterDefaultsToOpenOnlyRows() {
    InvestmentProduct open = fiiProduct("Open Fund", "ABCD11");
    InvestmentProduct closed = fiiProduct("Closed Fund", "EFGH11");
    holdingRepository.save(InvestmentHoldingMother.holding().withProductId(open.getId()).build());
    InvestmentHolding closedHolding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(closed.getId()).build());
    closedHolding.close(TODAY);
    holdingRepository.save(closedHolding);

    List<FiiPortfolioRow> openRows = query.portfolio(InvestmentProductStatus.OPEN, TODAY);
    List<FiiPortfolioRow> closedRows = query.portfolio(InvestmentProductStatus.CLOSED, TODAY);
    List<FiiPortfolioRow> allRows = query.portfolio(InvestmentProductStatus.ALL, TODAY);

    assertThat(openRows).extracting(FiiPortfolioRow::productId).containsExactly(open.getId());
    assertThat(closedRows).extracting(FiiPortfolioRow::productId).containsExactly(closed.getId());
    assertThat(allRows).hasSize(2);
  }

  @Test
  void rowCarriesTickerAndSegmentId() {
    UUID segmentId = UUID.randomUUID();
    InvestmentProduct product =
        productRepository.save(
            InvestmentProductMother.product()
                .withName("KNRI11 Fund")
                .withInvestmentSubcategoryId(fiiSubcategoryId)
                .withTicker("KNRI11")
                .withSegmentId(segmentId)
                .build());
    holdingRepository.save(
        InvestmentHoldingMother.holding().withProductId(product.getId()).build());

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL, TODAY);

    assertThat(rows.get(0).ticker()).isEqualTo("KNRI11");
    assertThat(rows.get(0).segmentId()).isEqualTo(segmentId);
  }

  @Test
  void tradesDatedAfterAsOfAreExcludedFromCotasHeldAndAmountContributed() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    UUID brokerId = UUID.randomUUID();
    holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(product.getId())
            .withAccountId(brokerId)
            .build());
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 10), // on or before asOf
            CHECKING_ID,
            brokerId,
            product.getId(),
            TradeSide.BUY,
            new BigDecimal("10"),
            BigDecimal.ONE,
            null));
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 4, 1), // after asOf (2026-03-01): must be excluded
            CHECKING_ID,
            brokerId,
            product.getId(),
            TradeSide.BUY,
            new BigDecimal("5"),
            BigDecimal.ONE,
            null));

    List<FiiPortfolioRow> rows =
        query.portfolio(InvestmentProductStatus.ALL, LocalDate.of(2026, 3, 1));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).cotasHeld()).isEqualByComparingTo("10");
    assertThat(rows.get(0).amountContributed()).isEqualByComparingTo("10.00");
  }

  @Test
  void currentValueAndLatestSnapshotDateUseTheSnapshotOnOrBeforeAsOf() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 3, 1),
            new BigDecimal("1000.00")));
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(),
            holding.getId(),
            LocalDate.of(2026, 5, 1),
            new BigDecimal("1200.00")));

    List<FiiPortfolioRow> rows =
        query.portfolio(InvestmentProductStatus.ALL, LocalDate.of(2026, 4, 1));

    assertThat(rows.get(0).currentValue()).isEqualByComparingTo("1000.00");
    assertThat(rows.get(0).latestSnapshotDate()).isEqualTo(LocalDate.of(2026, 3, 1));
  }

  @Test
  void needsSnapshotIsAlwaysFalseWhenAsOfIsNotToday() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    UUID brokerId = UUID.randomUUID();
    holdingRepository.save(
        InvestmentHoldingMother.holding()
            .withProductId(product.getId())
            .withAccountId(brokerId)
            .build());
    buy(product, brokerId, "100.00", "10"); // no snapshot ever recorded: stale as of today

    List<FiiPortfolioRow> todayRows = query.portfolio(InvestmentProductStatus.ALL, TODAY);
    List<FiiPortfolioRow> pastRows =
        query.portfolio(InvestmentProductStatus.ALL, TODAY.minusMonths(1));

    assertThat(todayRows.get(0).needsSnapshot()).isTrue();
    assertThat(pastRows.get(0).needsSnapshot()).isFalse();
  }

  private void buy(InvestmentProduct product, UUID brokerId, String unitPrice, String quantity) {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 10),
            CHECKING_ID,
            brokerId,
            product.getId(),
            TradeSide.BUY,
            new BigDecimal(quantity),
            new BigDecimal(unitPrice),
            null));
  }

  private void sell(InvestmentProduct product, UUID brokerId, String unitPrice, String quantity) {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 2, 10),
            brokerId,
            CHECKING_ID,
            product.getId(),
            TradeSide.SELL,
            new BigDecimal(quantity),
            new BigDecimal(unitPrice),
            null));
  }
}
