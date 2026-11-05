package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalStatus;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalDashboardQuery} (F039, ADR 0034), written first (ADR
 * 0004): only goals flagged "show on dashboard", an archived goal never, in the standard order,
 * each with its base-scenario status. Fakes only, fixed {@code Clock} (today is 2026-11-05).
 */
class GoalDashboardQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final GoalDashboardQuery query =
      new GoalDashboardQuery(
          goalRepository,
          allocationRepository,
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          TestClocks.fixedAtStartOf(TODAY));

  private Goal save(GoalMother mother) {
    return goalRepository.save(mother.build());
  }

  private void allocateWorth(Goal goal, String value, String percentage) {
    UUID productId = UUID.randomUUID();
    InvestmentHolding holding =
        holdingRepository.save(InvestmentHoldingMother.holding().withProductId(productId).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), TODAY.minusDays(1), new BigDecimal(value)));
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(
                UUID.randomUUID(), goal.getId(), productId, new BigDecimal(percentage))));
  }

  @Test
  void onlyFlaggedGoalsAppearAndAnArchivedGoalNeverDoes() {
    Goal shown = save(GoalMother.goal().withName("Shown"));
    save(GoalMother.goal().withName("Hidden").hiddenFromDashboard());
    save(GoalMother.goal().withName("Archived").withStatus(GoalStatus.ARCHIVED));
    Goal achieved = save(GoalMother.goal().withName("Achieved").withStatus(GoalStatus.ACHIEVED));

    assertThat(query.dashboard())
        .extracting(summary -> summary.goal().getId())
        .containsExactlyInAnyOrder(shown.getId(), achieved.getId());
  }

  @Test
  void anEmptyDashboardIsAnEmptyList() {
    save(GoalMother.goal().hiddenFromDashboard());

    assertThat(query.dashboard()).isEmpty();
  }

  @Test
  void datedGoalsComeFirstByDateThenDatelessByNameIgnoringCase() {
    save(GoalMother.goal().withName("zebra fund").dateless());
    save(GoalMother.goal().withName("Alpha fund").dateless());
    save(GoalMother.goal().withName("Later").withTargetDate(LocalDate.of(2030, 1, 1)));
    save(GoalMother.goal().withName("Sooner").withTargetDate(LocalDate.of(2027, 6, 1)));

    assertThat(query.dashboard())
        .extracting(summary -> summary.goal().getName())
        .containsExactly("Sooner", "Later", "Alpha fund", "zebra fund");
  }

  @Test
  void goalsWithTheSameDateOrNameTieBreakById() {
    UUID low = new UUID(0, 1);
    UUID high = new UUID(0, 2);
    save(GoalMother.goal().withId(high).withName("Same").withTargetDate(LocalDate.of(2028, 1, 1)));
    save(GoalMother.goal().withId(low).withName("Same").withTargetDate(LocalDate.of(2028, 1, 1)));
    save(GoalMother.goal().withId(new UUID(0, 4)).withName("Twin").dateless());
    save(GoalMother.goal().withId(new UUID(0, 3)).withName("twin").dateless());

    assertThat(query.dashboard())
        .extracting(summary -> summary.goal().getId())
        .containsExactly(low, high, new UUID(0, 3), new UUID(0, 4));
  }

  @Test
  void progressIsAllocatedOverTargetAndMayExceedOneHundredWithTheReachedFlag() {
    Goal goal = save(GoalMother.goal().withTargetAmount("1000"));
    allocateWorth(goal, "1500.00", "100");

    GoalProgress progress = query.dashboard().get(0).progress();

    assertThat(progress.allocatedValue()).isEqualByComparingTo("1500.00");
    assertThat(progress.progressPercent()).isEqualByComparingTo("150.00");
    assertThat(progress.targetReached()).isTrue();
  }

  @Test
  void aGoalBelowItsTargetIsNotReachedAndShowsItsProgress() {
    Goal goal = save(GoalMother.goal().withTargetAmount("3000"));
    allocateWorth(goal, "1000.00", "100");

    GoalProgress progress = query.dashboard().get(0).progress();

    assertThat(progress.progressPercent()).isEqualByComparingTo("33.33");
    assertThat(progress.targetReached()).isFalse();
  }

  @Test
  void theTargetIsReachedWhenTheValueEqualsIt() {
    Goal goal = save(GoalMother.goal().withTargetAmount("1000"));
    allocateWorth(goal, "1000.00", "100");

    assertThat(query.dashboard().get(0).progress().targetReached()).isTrue();
  }

  @Test
  void aDatedGoalCarriesTheBaseShortfallAndNoMonths() {
    // Target 20000 on 2027-11-20 (12 months), 10000 now, no contribution, 0% return: short by
    // 10000.
    Goal goal =
        save(
            GoalMother.goal()
                .withTargetAmount("20000")
                .withTargetDate(LocalDate.of(2027, 11, 20))
                .withContribution("0")
                .withReturns("0", "0", "0"));
    allocateWorth(goal, "10000.00", "100");

    GoalProgress progress = query.dashboard().get(0).progress();

    assertThat(progress.projectedShortfall()).isEqualByComparingTo("10000.00");
    assertThat(progress.monthsToTarget()).isNull();
  }

  @Test
  void aDatedGoalOnTrackHasANonPositiveShortfall() {
    Goal goal =
        save(
            GoalMother.goal()
                .withTargetAmount("20000")
                .withTargetDate(LocalDate.of(2027, 11, 20))
                .withContribution("1000")
                .withReturns("0", "0", "0"));
    allocateWorth(goal, "10000.00", "100");

    assertThat(query.dashboard().get(0).progress().projectedShortfall())
        .isEqualByComparingTo("-2000.00");
  }

  @Test
  void aDatelessGoalCarriesTheBaseMonthsAndNoShortfall() {
    Goal goal =
        save(
            GoalMother.goal()
                .dateless()
                .withTargetAmount("12000")
                .withContribution("500")
                .withReturns("0", "0", "0"));
    allocateWorth(goal, "10000.00", "100");

    GoalProgress progress = query.dashboard().get(0).progress();

    assertThat(progress.monthsToTarget()).isEqualTo(4);
    assertThat(progress.projectedShortfall()).isNull();
  }

  @Test
  void aDatelessGoalNeverReachedHasNullMonths() {
    save(
        GoalMother.goal()
            .dateless()
            .withTargetAmount("1000000000")
            .withContribution("0")
            .withReturns("0", "0", "0"));

    GoalProgress progress = query.dashboard().get(0).progress();

    assertThat(progress.monthsToTarget()).isNull();
    assertThat(progress.allocatedValue()).isEqualByComparingTo("0");
  }
}
