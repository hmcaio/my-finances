package com.chm.myfinances.application.networth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link NetWorthQuery#trend} (F010 spec), written first (ADR 0004): both granularities,
 * a value change landing on the right date, month points carrying values through quiet months, and
 * the current month evaluated at today.
 */
class NetWorthQueryTrendTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final NetWorthQuery query =
      new NetWorthQuery(
          accountRepository,
          new AccountBalanceQuery(
              transactionRepository,
              transferRepository,
              productRepository,
              new LatestInvestmentSnapshotQuery(snapshotRepository)),
          transactionRepository,
          transferRepository,
          snapshotRepository,
          Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

  private Account checking;
  private Account broker;

  @BeforeEach
  void setUp() {
    checking =
        accountRepository.save(
            AccountMother.checking()
                .withOpeningBalance(bd("1000.00"))
                .withOpeningBalanceDate(LocalDate.of(2026, 1, 1))
                .build());
    broker = accountRepository.save(AccountMother.investment().build());
  }

  private static BigDecimal bd(String value) {
    return new BigDecimal(value);
  }

  private static LocalDate d(int month, int day) {
    return LocalDate.of(2026, month, day);
  }

  private void expense(LocalDate date, String amount) {
    transactionRepository.save(
        TransactionMother.expense()
            .withAccountId(checking.getId())
            .withType(CategoryType.EXPENSE)
            .withAmount(bd(amount))
            .withDate(date)
            .build());
  }

  private void snapshot(LocalDate date, String balance) {
    InvestmentProduct product =
        productRepository.findAll().stream().findFirst().orElseGet(this::newProduct);
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), product.getId(), date, new BigDecimal(balance)));
  }

  private InvestmentProduct newProduct() {
    return productRepository.save(
        InvestmentProductMother.product().withAccountId(broker.getId()).withName("Fund").build());
  }

  private static List<LocalDate> dates(List<NetWorthPoint> points) {
    return points.stream().map(NetWorthPoint::date).toList();
  }

  @Test
  void changeDateHasAPointOnEveryTransactionTransferAndSnapshotDate() {
    Account savings =
        accountRepository.save(
            AccountMother.savings()
                .withOpeningBalance(bd("0.00"))
                .withOpeningBalanceDate(d(1, 1))
                .build());
    expense(d(2, 10), "100.00");
    transferRepository.save(
        TransferMother.transfer()
            .withDate(d(3, 5))
            .withFromAccountId(checking.getId())
            .withToAccountId(savings.getId())
            .withAmount(bd("200.00"))
            .build());
    snapshot(d(4, 20), "500.00");

    List<NetWorthPoint> points = query.trend(d(2, 1), d(5, 31), NetWorthGranularity.CHANGE_DATE);

    assertThat(dates(points)).containsExactly(d(2, 10), d(3, 5), d(4, 20));
  }

  @Test
  void changeDateAValueChangeIsReflectedOnItsOwnDate() {
    expense(d(2, 10), "100.00");
    snapshot(d(4, 20), "500.00");

    List<NetWorthPoint> points = query.trend(d(1, 1), d(5, 31), NetWorthGranularity.CHANGE_DATE);

    assertThat(points)
        .extracting(NetWorthPoint::date, p -> p.netWorth().setScale(2).toPlainString())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(d(1, 1), "1000.00"),
            org.assertj.core.groups.Tuple.tuple(d(2, 10), "900.00"),
            org.assertj.core.groups.Tuple.tuple(d(4, 20), "1400.00"));
    assertThat(points.get(2).investments()).isEqualByComparingTo("500.00");
    assertThat(points.get(2).assets()).isEqualByComparingTo("900.00");
  }

  @Test
  void changeDateAccountOpenedAndClosedDatesAreChangePointsToo() {
    Account extra =
        accountRepository.save(
            AccountMother.savings()
                .withOpeningBalance(bd("300.00"))
                .withOpeningBalanceDate(d(2, 1))
                .build());
    extra.close(d(3, 1));
    accountRepository.save(extra);

    List<NetWorthPoint> points = query.trend(d(2, 1), d(4, 1), NetWorthGranularity.CHANGE_DATE);

    assertThat(dates(points)).containsExactly(d(2, 1), d(3, 1));
    assertThat(points.get(0).netWorth()).isEqualByComparingTo("1300.00");
    assertThat(points.get(1).netWorth()).isEqualByComparingTo("1000.00");
  }

  @Test
  void changeDateDistinctDatesOnlyAndNothingOutsideTheRangeOrAfterToday() {
    expense(d(2, 10), "10.00");
    expense(d(2, 10), "20.00");
    expense(d(2, 9), "5.00");
    expense(d(7, 1), "1.00");

    List<NetWorthPoint> points = query.trend(d(2, 10), d(12, 31), NetWorthGranularity.CHANGE_DATE);

    assertThat(dates(points)).containsExactly(d(2, 10));
    assertThat(points.get(0).netWorth()).isEqualByComparingTo("965.00");
  }

  @Test
  void monthHasOnePointPerMonthDatedMonthEndAndCarriesValuesThroughQuietMonths() {
    expense(d(1, 20), "100.00");
    snapshot(d(4, 10), "700.00");

    List<NetWorthPoint> points = query.trend(d(1, 1), d(4, 30), NetWorthGranularity.MONTH);

    assertThat(dates(points)).containsExactly(d(1, 31), d(2, 28), d(3, 31), d(4, 30));
    assertThat(points)
        .extracting(p -> p.netWorth().setScale(2).toPlainString())
        .containsExactly("900.00", "900.00", "900.00", "1600.00");
  }

  @Test
  void monthATransactionOnAMonthEndIsInThatMonth() {
    expense(d(2, 28), "100.00");

    List<NetWorthPoint> points = query.trend(d(1, 1), d(3, 31), NetWorthGranularity.MONTH);

    assertThat(points.get(0).netWorth()).isEqualByComparingTo("1000.00");
    assertThat(points.get(1).netWorth()).isEqualByComparingTo("900.00");
  }

  @Test
  void monthCoversEveryMonthOverlappingTheRange() {
    List<NetWorthPoint> points = query.trend(d(1, 20), d(3, 5), NetWorthGranularity.MONTH);

    assertThat(dates(points)).containsExactly(d(1, 31), d(2, 28), d(3, 31));
  }

  @Test
  void monthTheCurrentMonthIsEvaluatedAtTodayAndLaterMonthsAreNotIncluded() {
    expense(d(6, 10), "100.00");
    expense(d(6, 20), "50.00");

    List<NetWorthPoint> points = query.trend(d(5, 1), d(9, 30), NetWorthGranularity.MONTH);

    assertThat(dates(points)).containsExactly(d(5, 31), TODAY);
    assertThat(points.get(1).netWorth()).isEqualByComparingTo("900.00");
  }

  @Test
  void aRangeEntirelyInTheFutureHasNoPoints() {
    assertThat(query.trend(d(8, 1), d(9, 1), NetWorthGranularity.MONTH)).isEmpty();
    assertThat(query.trend(d(8, 1), d(9, 1), NetWorthGranularity.CHANGE_DATE)).isEmpty();
  }

  @Test
  void aReversedRangeIsRejected() {
    assertThatThrownBy(() -> query.trend(d(3, 1), d(2, 1), NetWorthGranularity.MONTH))
        .isInstanceOf(InvalidNetWorthRangeException.class);
  }

  @Test
  void anExcessivelyLongRangeIsRejected() {
    assertThatThrownBy(
            () ->
                query.trend(
                    LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1), NetWorthGranularity.MONTH))
        .isInstanceOf(InvalidNetWorthRangeException.class);
  }
}
