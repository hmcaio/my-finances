package com.chm.myfinances.application.investmentreport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.InvestmentTradeDetails;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentValueSeriesQuery} (F009 spec), written first (ADR 0004): per product
 * and month-end (the current month at today) the latest snapshot value, the month's contributions
 * (buys minus sells, cash moved) and the running units. Raw data only.
 */
class InvestmentValueSeriesQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final Clock clock =
      Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
  private final InvestmentValueSeriesQuery query =
      new InvestmentValueSeriesQuery(
          productRepository, snapshotRepository, transferRepository, clock);

  private final UUID brokerId = UUID.randomUUID();
  private final UUID checkingId = UUID.randomUUID();
  private InvestmentProduct product;

  @BeforeEach
  void setUp() {
    product =
        productRepository.save(
            InvestmentProductMother.product().withAccountId(brokerId).withName("Fund").build());
  }

  private void snapshot(InvestmentProduct target, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), target.getId(), date, new BigDecimal(balance)));
  }

  private void buy(InvestmentProduct target, LocalDate date, String amount, String quantity) {
    trade(target, date, checkingId, brokerId, amount, quantity);
  }

  private void sell(InvestmentProduct target, LocalDate date, String amount, String quantity) {
    trade(target, date, brokerId, checkingId, amount, quantity);
  }

  private void trade(
      InvestmentProduct target,
      LocalDate date,
      UUID from,
      UUID to,
      String amount,
      String quantity) {
    InvestmentTradeDetails details =
        quantity == null
            ? null
            : new InvestmentTradeDetails(new BigDecimal(quantity), BigDecimal.ONE, null);
    transferRepository.save(
        TransferMother.transfer()
            .withDate(date)
            .withFromAccountId(from)
            .withToAccountId(to)
            .withAmount(new BigDecimal(amount))
            .withInvestmentProductId(target.getId())
            .withTradeDetails(details)
            .build());
  }

  private List<SeriesPoint> points(YearMonth from, YearMonth to) {
    List<ProductSeries> series = query.series(from, to, product.getId());
    assertThat(series).hasSize(1);
    assertThat(series.get(0).productId()).isEqualTo(product.getId());
    return series.get(0).points();
  }

  @Test
  void valueIsNullBeforeTheFirstSnapshotAndTheLatestOnOrBeforeMonthEndAfter() {
    snapshot(product, LocalDate.of(2026, 2, 10), "100.00");
    snapshot(product, LocalDate.of(2026, 2, 28), "120.00");
    snapshot(product, LocalDate.of(2026, 4, 5), "150.00");

    List<SeriesPoint> points = points(YearMonth.of(2026, 1), YearMonth.of(2026, 4));

    assertThat(points)
        .extracting(SeriesPoint::month)
        .containsExactly(
            YearMonth.of(2026, 1),
            YearMonth.of(2026, 2),
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4));
    assertThat(points.get(0).value()).isNull();
    assertThat(points.get(1).value()).isEqualByComparingTo("120.00");
    // No snapshot in March: the February value carries forward to month-end.
    assertThat(points.get(2).value()).isEqualByComparingTo("120.00");
    assertThat(points.get(3).value()).isEqualByComparingTo("150.00");
  }

  @Test
  void theCurrentMonthIsEvaluatedAtTodayNotAtMonthEnd() {
    snapshot(product, LocalDate.of(2026, 6, 10), "200.00");
    snapshot(product, LocalDate.of(2026, 6, 20), "999.00");

    List<SeriesPoint> points = points(YearMonth.of(2026, 5), YearMonth.of(2026, 6));

    assertThat(points.get(0).value()).isNull();
    assertThat(points.get(1).value()).isEqualByComparingTo("200.00");
  }

  @Test
  void aZeroSnapshotIsAValueNotAGap() {
    snapshot(product, LocalDate.of(2026, 2, 28), "0.00");

    assertThat(points(YearMonth.of(2026, 2), YearMonth.of(2026, 2)).get(0).value())
        .isEqualByComparingTo("0");
  }

  @Test
  void contributedIsTheMonthsBuysMinusSellsAsCashMoved() {
    buy(product, LocalDate.of(2026, 3, 5), "1000.00", null);
    buy(product, LocalDate.of(2026, 3, 20), "500.50", null);
    sell(product, LocalDate.of(2026, 3, 25), "400.00", null);
    sell(product, LocalDate.of(2026, 4, 2), "100.00", null);

    List<SeriesPoint> points = points(YearMonth.of(2026, 2), YearMonth.of(2026, 4));

    assertThat(points.get(0).contributed()).isEqualByComparingTo("0");
    assertThat(points.get(1).contributed()).isEqualByComparingTo("1100.50");
    assertThat(points.get(2).contributed()).isEqualByComparingTo("-100.00");
  }

  @Test
  void contributedIgnoresOtherProductsAndTradesOutsideTheMonth() {
    InvestmentProduct other =
        productRepository.save(
            InvestmentProductMother.product().withAccountId(brokerId).withName("Other").build());
    buy(other, LocalDate.of(2026, 3, 5), "777.00", null);
    buy(product, LocalDate.of(2026, 2, 28), "10.00", null);
    buy(product, LocalDate.of(2026, 4, 1), "20.00", null);

    assertThat(points(YearMonth.of(2026, 3), YearMonth.of(2026, 3)).get(0).contributed())
        .isEqualByComparingTo("0");
  }

  @Test
  void unitsAreTheRunningBuysMinusSellsOfQuantity() {
    buy(product, LocalDate.of(2026, 2, 10), "1000.00", "10");
    buy(product, LocalDate.of(2026, 3, 10), "500.00", "5.5");
    sell(product, LocalDate.of(2026, 4, 10), "300.00", "3");

    List<SeriesPoint> points = points(YearMonth.of(2026, 1), YearMonth.of(2026, 5));

    // Before the first trade a product that does have quantities holds 0 units.
    assertThat(points.get(0).units()).isEqualByComparingTo("0");
    assertThat(points.get(1).units()).isEqualByComparingTo("10");
    assertThat(points.get(2).units()).isEqualByComparingTo("15.5");
    assertThat(points.get(3).units()).isEqualByComparingTo("12.5");
    assertThat(points.get(4).units()).isEqualByComparingTo("12.5");
  }

  @Test
  void unitsAreNullWhenTheProductHasNoRecordedQuantities() {
    buy(product, LocalDate.of(2026, 2, 10), "1000.00", null);
    sell(product, LocalDate.of(2026, 3, 10), "300.00", null);

    List<SeriesPoint> points = points(YearMonth.of(2026, 1), YearMonth.of(2026, 3));

    assertThat(points).allSatisfy(p -> assertThat(p.units()).isNull());
    assertThat(points.get(1).contributed()).isEqualByComparingTo("1000.00");
  }

  @Test
  void aTradeWithoutQuantityMovesCashButNotUnits() {
    buy(product, LocalDate.of(2026, 2, 10), "1000.00", "10");
    buy(product, LocalDate.of(2026, 3, 10), "50.00", null);

    List<SeriesPoint> points = points(YearMonth.of(2026, 2), YearMonth.of(2026, 3));

    assertThat(points.get(1).units()).isEqualByComparingTo("10");
    assertThat(points.get(1).contributed()).isEqualByComparingTo("50.00");
  }

  @Test
  void withoutAProductIdThereIsOneSeriesPerProduct() {
    InvestmentProduct other =
        productRepository.save(
            InvestmentProductMother.product().withAccountId(brokerId).withName("Other").build());
    snapshot(other, LocalDate.of(2026, 3, 31), "42.00");

    List<ProductSeries> series = query.series(YearMonth.of(2026, 3), YearMonth.of(2026, 3), null);

    assertThat(series)
        .extracting(ProductSeries::productId)
        .containsExactlyInAnyOrder(product.getId(), other.getId());
    assertThat(
            series.stream()
                .filter(s -> s.productId().equals(other.getId()))
                .findFirst()
                .orElseThrow()
                .points()
                .get(0)
                .value())
        .isEqualByComparingTo("42.00");
  }

  @Test
  void monthsAfterTheCurrentMonthAreNotIncluded() {
    List<SeriesPoint> points = points(YearMonth.of(2026, 5), YearMonth.of(2026, 9));

    assertThat(points)
        .extracting(SeriesPoint::month)
        .containsExactly(YearMonth.of(2026, 5), YearMonth.of(2026, 6));
  }

  @Test
  void anUnknownProductIsNotFound() {
    assertThatThrownBy(
            () -> query.series(YearMonth.of(2026, 1), YearMonth.of(2026, 2), UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void aReversedRangeIsRejected() {
    assertThatThrownBy(() -> query.series(YearMonth.of(2026, 3), YearMonth.of(2026, 1), null))
        .isInstanceOf(InvalidValueSeriesRangeException.class);
  }

  @Test
  void anExcessiveRangeIsRejected() {
    assertThatThrownBy(() -> query.series(YearMonth.of(1990, 1), YearMonth.of(2026, 1), null))
        .isInstanceOf(InvalidValueSeriesRangeException.class);
  }
}
