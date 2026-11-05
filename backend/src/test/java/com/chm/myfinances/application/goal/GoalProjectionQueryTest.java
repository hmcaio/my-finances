package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeGoalAllocationRepository;
import com.chm.myfinances.testsupport.fakes.FakeGoalRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalProjectionQuery} (F039, ADR 0034), written first (ADR
 * 0004) against hand-written fakes and a fixed {@code Clock} in the pinned non-UTC zone. "Today" is
 * 2026-11-05, so a target date in November 2027 is 12 months away.
 */
class GoalProjectionQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
  private static final LocalDate IN_TWELVE_MONTHS = LocalDate.of(2027, 11, 20);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final GoalProjectionQuery query =
      new GoalProjectionQuery(
          goalRepository,
          allocationRepository,
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          TestClocks.fixedAtStartOf(TODAY));

  /** A product whose single holding was last valued at {@code value} on {@code snapshotDate}. */
  private UUID productWorth(String value, LocalDate snapshotDate) {
    UUID productId = UUID.randomUUID();
    InvestmentHolding holding =
        holdingRepository.save(InvestmentHoldingMother.holding().withProductId(productId).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), snapshotDate, new BigDecimal(value)));
    return productId;
  }

  private void allocate(Goal goal, UUID productId, String percentage) {
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(
                UUID.randomUUID(), goal.getId(), productId, new BigDecimal(percentage))));
  }

  /** A goal holding all of a product worth {@code startingValue}. */
  private Goal goalWorth(GoalMother mother, String startingValue) {
    Goal goal = goalRepository.save(mother.build());
    allocate(goal, productWorth(startingValue, TODAY.minusDays(3)), "100");
    return goal;
  }

  private ScenarioProjection scenario(GoalProjection projection, ProjectionScenario which) {
    return projection.scenarios().stream()
        .filter(s -> s.scenario() == which)
        .findFirst()
        .orElseThrow();
  }

  // --- the series ---

  @Test
  void zeroReturnAndZeroContributionLeavesTheValueFlat() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withContribution("0")
                .withReturns("0", "0", "0"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.points()).hasSize(13);
    assertThat(base.points())
        .allSatisfy(p -> assertThat(p.value()).isEqualByComparingTo("10000.00"));
    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("10000.00");
  }

  @Test
  void zeroReturnEqualsTheStartingValuePlusMonthsTimesTheContribution() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withContribution("500")
                .withReturns("0", "0", "0"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("16000.00");
    assertThat(base.points().get(5).value()).isEqualByComparingTo("12500.00");
  }

  @Test
  void aTwelveMonthCaseMatchesTheClosedFormToTheCent() {
    // i = 1.06^(1/12) - 1; V12 = 10000 * 1.06 + 500 * 0.06 / i = 16763.264... (contributions at
    // month end)
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withContribution("500")
                .withReturns("6", "6", "6"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("16763.26");
    assertThat(base.shortfallAtTargetDate()).isEqualByComparingTo("63236.74");
  }

  @Test
  void aNegativeRealReturnShrinksTheValue() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withContribution("0")
                .withReturns("-10", "-10", "-10"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("9000.00");
  }

  @Test
  void pointsRunMonthlyFromTheCurrentMonthThroughTheTargetMonth() {
    Goal goal = goalWorth(GoalMother.goal().withTargetDate(IN_TWELVE_MONTHS), "1000.00");

    List<ProjectionPoint> points =
        scenario(query.projection(goal.getId()), ProjectionScenario.BASE).points();

    assertThat(points.get(0).month()).isEqualTo(YearMonth.of(2026, 11));
    assertThat(points.get(0).value()).isEqualByComparingTo("1000.00");
    assertThat(points.get(12).month()).isEqualTo(YearMonth.of(2027, 11));
  }

  @Test
  void aTargetLaterThisMonthProjectsOverZeroMonths() {
    Goal goal = goalWorth(GoalMother.goal().withTargetDate(TODAY.plusDays(10)), "1000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.points()).hasSize(1);
    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("1000.00");
  }

  @Test
  void aPastTargetDateProjectsOverZeroMonthsAndReadsAsAPlainShortfall() {
    Goal goal =
        goalWorth(
            GoalMother.goal().withTargetDate(TODAY.minusMonths(2)).withTargetAmount("5000"),
            "1000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.points()).hasSize(1);
    assertThat(base.projectedValueAtTargetDate()).isEqualByComparingTo("1000.00");
    assertThat(base.shortfallAtTargetDate()).isEqualByComparingTo("4000.00");
    assertThat(base.requiredMonthlyContribution()).isNull();
    assertThat(base.monthsToTarget()).isNull();
  }

  @Test
  void aShortfallIsNegativeWhenTheTargetIsExceeded() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withTargetAmount("5000")
                .withContribution("0")
                .withReturns("0", "0", "0"),
            "7000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.shortfallAtTargetDate()).isEqualByComparingTo("-2000.00");
  }

  // --- required contribution ---

  @Test
  void theRequiredContributionIsZeroWhenTheStartingValueAlreadyReachesTheTarget() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withTargetAmount("5000")
                .withContribution("0")
                .withReturns("0", "2", "4"),
            "6000.00");

    GoalProjection projection = query.projection(goal.getId());

    assertThat(projection.scenarios())
        .allSatisfy(s -> assertThat(s.requiredMonthlyContribution()).isEqualByComparingTo("0"));
  }

  @Test
  void withZeroReturnTheRequiredContributionIsTheGapSpreadOverTheMonths() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withTargetAmount("20000")
                .withContribution("0")
                .withReturns("0", "0", "0"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    // 10000 / 12 = 833.333..., rounded up to the cent so the target is certainly reached.
    assertThat(base.requiredMonthlyContribution()).isEqualByComparingTo("833.34");
  }

  @Test
  void feedingTheRequiredContributionBackIntoTheProjectionReachesTheTarget() {
    LocalDate inTwoYears = LocalDate.of(2028, 11, 20);
    Goal first =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(inTwoYears)
                .withTargetAmount("50000")
                .withContribution("0")
                .withReturns("2", "4", "6"),
            "10000.00");
    BigDecimal required =
        scenario(query.projection(first.getId()), ProjectionScenario.BASE)
            .requiredMonthlyContribution();
    assertThat(required.signum()).isPositive();

    Goal second =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(inTwoYears)
                .withTargetAmount("50000")
                .withContribution(required.toPlainString())
                .withReturns("2", "4", "6"),
            "10000.00");
    BigDecimal reached =
        scenario(query.projection(second.getId()), ProjectionScenario.BASE)
            .projectedValueAtTargetDate();

    assertThat(reached).isBetween(new BigDecimal("50000.00"), new BigDecimal("50001.00"));
  }

  @Test
  void aBetterScenarioNeedsASmallerContribution() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(LocalDate.of(2031, 11, 20))
                .withTargetAmount("100000")
                .withContribution("0")
                .withReturns("1", "4", "8"),
            "10000.00");

    GoalProjection projection = query.projection(goal.getId());

    assertThat(scenario(projection, ProjectionScenario.PESSIMISTIC).requiredMonthlyContribution())
        .isGreaterThan(scenario(projection, ProjectionScenario.BASE).requiredMonthlyContribution());
    assertThat(scenario(projection, ProjectionScenario.BASE).requiredMonthlyContribution())
        .isGreaterThan(
            scenario(projection, ProjectionScenario.OPTIMISTIC).requiredMonthlyContribution());
  }

  // --- scenarios ---

  @Test
  void theThreeScenariosAreOrderedAndEchoTheirReturns() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .withTargetDate(LocalDate.of(2031, 11, 20))
                .withContribution("1000")
                .withReturns("2", "4", "6"),
            "10000.00");

    GoalProjection projection = query.projection(goal.getId());

    assertThat(projection.scenarios())
        .extracting(ScenarioProjection::scenario)
        .containsExactly(
            ProjectionScenario.PESSIMISTIC, ProjectionScenario.BASE, ProjectionScenario.OPTIMISTIC);
    assertThat(projection.scenarios())
        .extracting(s -> s.annualReturn().intValue())
        .containsExactly(2, 4, 6);
    BigDecimal pessimistic =
        scenario(projection, ProjectionScenario.PESSIMISTIC).projectedValueAtTargetDate();
    BigDecimal base = scenario(projection, ProjectionScenario.BASE).projectedValueAtTargetDate();
    BigDecimal optimistic =
        scenario(projection, ProjectionScenario.OPTIMISTIC).projectedValueAtTargetDate();
    assertThat(pessimistic).isLessThan(base);
    assertThat(base).isLessThan(optimistic);
  }

  // --- dateless goals ---

  @Test
  void aDatelessGoalAlreadyReachedReportsZeroMonthsAndASinglePoint() {
    Goal goal =
        goalWorth(
            GoalMother.goal().dateless().withTargetAmount("5000").withReturns("0", "0", "0"),
            "6000.00");

    GoalProjection projection = query.projection(goal.getId());

    assertThat(projection.scenarios())
        .allSatisfy(
            s -> {
              assertThat(s.monthsToTarget()).isZero();
              assertThat(s.points()).hasSize(1);
              assertThat(s.projectedValueAtTargetDate()).isNull();
              assertThat(s.shortfallAtTargetDate()).isNull();
              assertThat(s.requiredMonthlyContribution()).isNull();
            });
  }

  @Test
  void aDatelessGoalReportsTheFirstMonthTheValueReachesTheTarget() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .dateless()
                .withTargetAmount("12000")
                .withContribution("500")
                .withReturns("0", "0", "0"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    // 10000 + 500 m >= 12000 first at m = 4; the series runs 0..4.
    assertThat(base.monthsToTarget()).isEqualTo(4);
    assertThat(base.points()).hasSize(5);
    assertThat(base.points().get(4).value()).isEqualByComparingTo("12000.00");
  }

  @Test
  void aDatelessGoalRunsUntilThePessimisticScenarioReachesTheTarget() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .dateless()
                .withTargetAmount("12000")
                .withContribution("500")
                .withReturns("0", "50", "100"),
            "10000.00");

    GoalProjection projection = query.projection(goal.getId());

    assertThat(scenario(projection, ProjectionScenario.PESSIMISTIC).monthsToTarget()).isEqualTo(4);
    assertThat(scenario(projection, ProjectionScenario.BASE).monthsToTarget()).isLessThan(4);
    assertThat(projection.scenarios()).allSatisfy(s -> assertThat(s.points()).hasSize(5));
  }

  @Test
  void aDatelessGoalNeverReachedWithinFiftyYearsHasNoMonthsAndStopsAtSixHundred() {
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .dateless()
                .withTargetAmount("1000000000")
                .withContribution("0")
                .withReturns("0", "0", "0"),
            "10000.00");

    ScenarioProjection base = scenario(query.projection(goal.getId()), ProjectionScenario.BASE);

    assertThat(base.monthsToTarget()).isNull();
    assertThat(base.points()).hasSize(601);
    assertThat(base.points().get(600).month()).isEqualTo(YearMonth.of(2076, 11));
  }

  @Test
  void aDatelessGoalReachedExactlyAtTheCapIsReported() {
    // 10000 + 100 m reaches 70000 at m = 600 exactly (the last simulated month).
    Goal goal =
        goalWorth(
            GoalMother.goal()
                .dateless()
                .withTargetAmount("70000")
                .withContribution("100")
                .withReturns("0", "0", "0"),
            "10000.00");

    assertThat(scenario(query.projection(goal.getId()), ProjectionScenario.BASE).monthsToTarget())
        .isEqualTo(600);
  }

  // --- the starting value ---

  @Test
  void theStartingValueIsTheSumOfEachProductsShareOfItsLatestSnapshot() {
    Goal goal =
        goalRepository.save(
            GoalMother.goal()
                .withTargetDate(IN_TWELVE_MONTHS)
                .withContribution("0")
                .withReturns("0", "0", "0")
                .build());
    UUID first = productWorth("1000.00", LocalDate.of(2026, 10, 20));
    UUID second = productWorth("500.00", LocalDate.of(2026, 11, 1));
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), first, new BigDecimal("60")),
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), second, new BigDecimal("40"))));

    GoalProjection projection = query.projection(goal.getId());

    assertThat(projection.allocatedValue()).isEqualByComparingTo("800.00");
    assertThat(projection.latestSnapshotDate()).isEqualTo(LocalDate.of(2026, 11, 1));
  }

  @Test
  void aProductWithNoSnapshotCountsAsZeroAndNothingAllocatedHasNoSnapshotDate() {
    Goal goal = goalRepository.save(GoalMother.goal().withTargetDate(IN_TWELVE_MONTHS).build());
    UUID unvalued = UUID.randomUUID();
    holdingRepository.save(InvestmentHoldingMother.holding().withProductId(unvalued).build());
    allocate(goal, unvalued, "50");

    GoalProjection withUnvalued = query.projection(goal.getId());
    assertThat(withUnvalued.allocatedValue()).isEqualByComparingTo("0");
    assertThat(withUnvalued.latestSnapshotDate()).isNull();

    allocationRepository.replaceForGoal(goal.getId(), List.of());
    assertThat(query.projection(goal.getId()).allocatedValue()).isEqualByComparingTo("0");
  }

  @Test
  void aSnapshotAfterTodayDoesNotCount() {
    Goal goal = goalRepository.save(GoalMother.goal().withTargetDate(IN_TWELVE_MONTHS).build());
    allocate(goal, productWorth("9999.00", TODAY.plusDays(1)), "100");

    assertThat(query.projection(goal.getId()).allocatedValue()).isEqualByComparingTo("0");
  }

  @Test
  void anUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> query.projection(UUID.randomUUID()))
        .isInstanceOf(GoalNotFoundException.class);
  }
}
