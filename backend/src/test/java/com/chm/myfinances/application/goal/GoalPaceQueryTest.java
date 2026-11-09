package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeGoalAllocationRepository;
import com.chm.myfinances.testsupport.fakes.FakeGoalRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentOpeningPositionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalPaceQuery} (F041 spec, ADR 0035), written first (ADR
 * 0004): the average monthly increase of the goal's value over the last 12 months of history,
 * skipping the months before the first value and the ones up to the latest opening position, as a
 * straight line from the value now. Fakes only, fixed {@code Clock} in the pinned non-UTC zone
 * (today is 2026-11-05, so the window is 2025-12 through 2026-11).
 */
class GoalPaceQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
  private static final YearMonth NOW = YearMonth.of(2026, 11);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentOpeningPositionRepository openingPositionRepository =
      new FakeInvestmentOpeningPositionRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final GoalPaceQuery query =
      new GoalPaceQuery(
          new GoalHistoryQuery(
              goalRepository,
              allocationRepository,
              new InvestmentValueSeriesQuery(
                  productRepository,
                  holdingRepository,
                  snapshotRepository,
                  new FakeTransferTradeLineRepository(),
                  openingPositionRepository,
                  clock),
              clock),
          allocationRepository,
          holdingRepository,
          openingPositionRepository);

  private UUID newProduct(String name) {
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName(name).build());
    holdingRepository.save(
        InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    return product.getId();
  }

  /** One snapshot on the 20th of each month from {@code first}, one value per month. */
  private void monthlySnapshots(UUID productId, YearMonth first, String... balances) {
    InvestmentHolding holding = holdingRepository.findByProductId(productId).get(0);
    for (int i = 0; i < balances.length; i++) {
      snapshotRepository.save(
          InvestmentSnapshot.create(
              UUID.randomUUID(),
              holding.getId(),
              first.plusMonths(i).atDay(20),
              new BigDecimal(balances[i])));
    }
  }

  private void openingPosition(UUID productId, LocalDate asOf) {
    InvestmentHolding holding = holdingRepository.findByProductId(productId).get(0);
    openingPositionRepository.save(
        InvestmentOpeningPosition.create(
            UUID.randomUUID(), holding.getId(), asOf, new BigDecimal("10"), new BigDecimal("500")));
  }

  private Goal goalAllocating(GoalMother mother, UUID... productIds) {
    Goal goal = goalRepository.save(mother.build());
    List<GoalAllocation> allocations = new ArrayList<>();
    for (UUID productId : productIds) {
      allocations.add(
          GoalAllocation.create(UUID.randomUUID(), goal.getId(), productId, new BigDecimal("100")));
    }
    allocationRepository.replaceForGoal(goal.getId(), allocations);
    return goal;
  }

  private Optional<PaceProjection> paceOf(Goal goal, String allocatedValue, int horizon) {
    return query.pace(goal, new BigDecimal(allocatedValue), horizon, TODAY);
  }

  @Test
  void leadingMonthsWithNoValueAreSkipped() {
    UUID a = newProduct("A");
    // Nothing until 2026-06: the window is 2026-06..2026-11, six points, five intervals.
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    Optional<PaceProjection> pace = paceOf(goal, "1500.00", 3);

    assertThat(pace).isPresent();
    assertThat(pace.get().monthlyPace()).isEqualByComparingTo("100.00");
  }

  @Test
  void theWindowStartsAfterTheLatestOpeningPositionOfAnAllocatedProduct() {
    UUID a = newProduct("A");
    UUID b = newProduct("B");
    // A held 1000 until May, then an opening position in June lands its value in one jump.
    monthlySnapshots(
        a, YearMonth.of(2026, 5), "1000", "5000", "5100", "5200", "5300", "5400", "5500");
    openingPosition(a, LocalDate.of(2026, 6, 10));
    // B's opening position is earlier (March), so A's June one is the latest.
    monthlySnapshots(b, YearMonth.of(2026, 3), "0", "0", "0", "0", "0", "0", "0", "0");
    openingPosition(b, LocalDate.of(2026, 3, 10));
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a, b);

    Optional<PaceProjection> pace = paceOf(goal, "5400.00", 3);

    // From 2026-07 (5100) to now (5400 passed in): 300 / 4 = 75. Counting the jump would give
    // (5400 - 1000) / 6 = 733.33.
    assertThat(pace).isPresent();
    assertThat(pace.get().monthlyPace()).isEqualByComparingTo("75.00");
  }

  @Test
  void anOpeningPositionOfAProductTheGoalDoesNotHoldIsIgnored() {
    UUID a = newProduct("A");
    UUID other = newProduct("Other");
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    openingPosition(other, LocalDate.of(2026, 10, 10));
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    assertThat(paceOf(goal, "1500.00", 3)).isPresent();
  }

  @Test
  void fewerThanFourPointsLeftIsEmpty() {
    UUID a = newProduct("A");
    // Only 2026-09, 2026-10 and 2026-11 have a value: three points.
    monthlySnapshots(a, YearMonth.of(2026, 9), "1000", "1100", "1200");
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    assertThat(paceOf(goal, "1200.00", 3)).isEmpty();
  }

  @Test
  void exactlyFourPointsIsEnough() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 8), "1000", "1100", "1200", "1300");
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    Optional<PaceProjection> pace = paceOf(goal, "1300.00", 3);

    assertThat(pace).isPresent();
    assertThat(pace.get().monthlyPace()).isEqualByComparingTo("100.00");
  }

  @Test
  void anOpeningPositionInTheLastMonthsLeavesTooFewPoints() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 5), "1000", "1100", "1200", "1300", "1400", "5400");
    openingPosition(a, LocalDate.of(2026, 9, 10));
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    // The window would start in 2026-10: October and November only.
    assertThat(paceOf(goal, "5400.00", 3)).isEmpty();
  }

  @Test
  void noHistoryAtAllIsEmpty() {
    UUID a = newProduct("A");
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    assertThat(paceOf(goal, "0.00", 3)).isEmpty();
  }

  @Test
  void aGoalWithNothingAllocatedIsEmpty() {
    Goal goal = goalRepository.save(GoalMother.goal().dateless().build());

    assertThat(paceOf(goal, "0.00", 3)).isEmpty();
  }

  @Test
  void theLineStartsAtTheAllocatedValueNowAndThePaceEndsThere() {
    UUID a = newProduct("A");
    // The history's last point is 1500, but the goal's value now (the scenarios' start) is 1600.
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    Goal goal = goalAllocating(GoalMother.goal().dateless(), a);

    PaceProjection pace = paceOf(goal, "1600.00", 3).orElseThrow();

    // (1600 - 1000) / 5.
    assertThat(pace.monthlyPace()).isEqualByComparingTo("120.00");
    assertThat(pace.points())
        .extracting(ProjectionPoint::month)
        .containsExactly(NOW, NOW.plusMonths(1), NOW.plusMonths(2), NOW.plusMonths(3));
    assertThat(pace.points().get(0).value()).isEqualByComparingTo("1600.00");
    assertThat(pace.points().get(1).value()).isEqualByComparingTo("1720.00");
    assertThat(pace.points().get(3).value()).isEqualByComparingTo("1960.00");
  }

  @Test
  void aDatedGoalGetsTheValueAtTheTargetDateAndTheShortfall() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    Goal goal =
        goalAllocating(
            GoalMother.goal().withTargetDate(LocalDate.of(2027, 2, 20)).withTargetAmount("2000"),
            a);

    PaceProjection pace = paceOf(goal, "1500.00", 3).orElseThrow();

    assertThat(pace.points()).hasSize(4);
    assertThat(pace.projectedValueAtTargetDate()).isEqualByComparingTo("1800.00");
    assertThat(pace.shortfallAtTargetDate()).isEqualByComparingTo("200.00");
    assertThat(pace.monthsToTarget()).isNull();
  }

  @Test
  void aDatelessGoalGetsMonthsToTargetAndNeverMorePointsThanTheScenariosHorizon() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    Goal goal = goalAllocating(GoalMother.goal().dateless().withTargetAmount("2000"), a);

    PaceProjection pace = paceOf(goal, "1500.00", 2).orElseThrow();

    // 500 more at 100 a month: five months, though the line stops at the scenarios' horizon.
    assertThat(pace.monthsToTarget()).isEqualTo(5);
    assertThat(pace.points()).hasSize(3);
    assertThat(pace.projectedValueAtTargetDate()).isNull();
    assertThat(pace.shortfallAtTargetDate()).isNull();
  }

  @Test
  void aDatelessGoalAlreadyAtItsTargetIsZeroMonths() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 6), "1000", "1100", "1200", "1300", "1400", "1500");
    Goal goal = goalAllocating(GoalMother.goal().dateless().withTargetAmount("1500"), a);

    assertThat(paceOf(goal, "1500.00", 2).orElseThrow().monthsToTarget()).isZero();
  }

  @Test
  void aNegativePaceFallsAndNeverReachesTheTarget() {
    UUID a = newProduct("A");
    monthlySnapshots(a, YearMonth.of(2026, 6), "2000", "1900", "1800", "1700", "1600", "1500");
    Goal dateless = goalAllocating(GoalMother.goal().dateless().withTargetAmount("5000"), a);
    Goal dated =
        goalAllocating(
            GoalMother.goal().withTargetDate(LocalDate.of(2027, 2, 20)).withTargetAmount("5000"),
            a);

    PaceProjection falling = paceOf(dateless, "1500.00", 3).orElseThrow();
    PaceProjection datedFalling = paceOf(dated, "1500.00", 3).orElseThrow();

    assertThat(falling.monthlyPace()).isEqualByComparingTo("-100.00");
    assertThat(falling.points().get(3).value()).isEqualByComparingTo("1200.00");
    assertThat(falling.monthsToTarget()).isNull();
    assertThat(datedFalling.projectedValueAtTargetDate()).isEqualByComparingTo("1200.00");
    assertThat(datedFalling.shortfallAtTargetDate()).isEqualByComparingTo("3800.00");
  }
}
