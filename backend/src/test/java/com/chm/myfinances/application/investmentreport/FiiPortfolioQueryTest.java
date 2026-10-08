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
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FiiPortfolioQuery} (F026 spec, ADR 0023): per FII product, cotas held/amount
 * contributed as a running total across every holding/account, current value/needsSnapshot rolled
 * up from holdings, and the status filter.
 */
class FiiPortfolioQueryTest {

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
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final InvestmentSnapshotFreshnessQuery freshnessQuery =
      new InvestmentSnapshotFreshnessQuery(
          latestSnapshotQuery, transferRepository, holdingRepository);
  private final FiiPortfolioQuery query =
      new FiiPortfolioQuery(
          productRepository,
          subcategoryRepository,
          holdingRepository,
          transferRepository,
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

    assertThat(query.portfolio(InvestmentProductStatus.ALL)).isEmpty();
  }

  @Test
  void cotasHeldAndAmountContributedMatchHandComputedSumsAcrossTwoHoldings() {
    InvestmentProduct product = fiiProduct("KNRI11 Fund", "KNRI11");
    UUID checkingId = UUID.randomUUID();
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

    buy(product, checkingId, brokerAId, "1000.00", "10");
    buy(product, checkingId, brokerBId, "500.00", "5");

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL);

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
    UUID checkingId = UUID.randomUUID();
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

    buy(knri, checkingId, brokerId, "1000.00", "10");
    sell(knri, checkingId, brokerId, "1050.00", "10");
    buy(hglg, checkingId, brokerId, "200.00", "2");

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL);

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
    UUID checkingId = UUID.randomUUID();
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
    buy(product, checkingId, brokerAId, "100.00", "1");
    // A trade after the last snapshot makes holdingA stale.
    InvestmentProduct target = product;
    transferRepository.save(
        TransferMother.transfer()
            .withDate(LocalDate.of(2026, 5, 2))
            .withFromAccountId(checkingId)
            .withToAccountId(brokerAId)
            .withAmount(new BigDecimal("100.00"))
            .withInvestmentProductId(target.getId())
            .withTradeDetails(new InvestmentTradeDetails(new BigDecimal("1"), BigDecimal.ONE, null))
            .build());

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL);

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

    List<FiiPortfolioRow> openRows = query.portfolio(InvestmentProductStatus.OPEN);
    List<FiiPortfolioRow> closedRows = query.portfolio(InvestmentProductStatus.CLOSED);
    List<FiiPortfolioRow> allRows = query.portfolio(InvestmentProductStatus.ALL);

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

    List<FiiPortfolioRow> rows = query.portfolio(InvestmentProductStatus.ALL);

    assertThat(rows.get(0).ticker()).isEqualTo("KNRI11");
    assertThat(rows.get(0).segmentId()).isEqualTo(segmentId);
  }

  private void buy(InvestmentProduct product, UUID from, UUID to, String amount, String quantity) {
    transferRepository.save(
        TransferMother.transfer()
            .withDate(LocalDate.of(2026, 1, 10))
            .withFromAccountId(from)
            .withToAccountId(to)
            .withAmount(new BigDecimal(amount))
            .withInvestmentProductId(product.getId())
            .withTradeDetails(
                new InvestmentTradeDetails(new BigDecimal(quantity), BigDecimal.ONE, null))
            .build());
  }

  private void sell(InvestmentProduct product, UUID to, UUID from, String amount, String quantity) {
    transferRepository.save(
        TransferMother.transfer()
            .withDate(LocalDate.of(2026, 2, 10))
            .withFromAccountId(from)
            .withToAccountId(to)
            .withAmount(new BigDecimal(amount))
            .withInvestmentProductId(product.getId())
            .withTradeDetails(
                new InvestmentTradeDetails(new BigDecimal(quantity), BigDecimal.ONE, null))
            .build());
  }
}
