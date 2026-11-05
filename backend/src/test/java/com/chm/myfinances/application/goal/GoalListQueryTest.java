package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeGoalAllocationRepository;
import com.chm.myfinances.testsupport.fakes.FakeGoalRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.GoalMother;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalListQuery} (F039, ADR 0034), written first (ADR 0004):
 * each goal with its allocations (product name, percentage), allocated value and progress, in the
 * standard order, archived goals only on request. Fakes only, fixed {@code Clock} (2026-11-05).
 */
class GoalListQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final GoalListQuery query =
      new GoalListQuery(
          goalRepository,
          allocationRepository,
          productRepository,
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          TestClocks.fixedAtStartOf(TODAY));

  private Goal save(GoalMother mother) {
    return goalRepository.save(mother.build());
  }

  private UUID productWorth(String name, String value) {
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName(name).build());
    InvestmentHolding holding =
        holdingRepository.save(
            InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), TODAY.minusDays(1), new BigDecimal(value)));
    return product.getId();
  }

  private void allocate(Goal goal, UUID first, String firstPct, UUID second, String secondPct) {
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), first, new BigDecimal(firstPct)),
            GoalAllocation.create(
                UUID.randomUUID(), goal.getId(), second, new BigDecimal(secondPct))));
  }

  @Test
  void listsEachGoalWithItsAllocationsAndProgress() {
    Goal goal = save(GoalMother.goal().withTargetAmount("1000"));
    UUID cdb = productWorth("CDB Banco", "1000.00");
    UUID fund = productWorth("Fundo Renda", "500.00");
    allocate(goal, cdb, "30", fund, "60");

    GoalOverview overview = query.list(false).get(0);

    assertThat(overview.goal().getId()).isEqualTo(goal.getId());
    // 30% of 1000 + 60% of 500 = 600.
    assertThat(overview.progress().allocatedValue()).isEqualByComparingTo("600.00");
    assertThat(overview.progress().progressPercent()).isEqualByComparingTo("60.00");
    assertThat(overview.progress().targetReached()).isFalse();
    assertThat(overview.allocations())
        .extracting(GoalOverview.AllocationDetail::productName)
        .containsExactly("Fundo Renda", "CDB Banco");
    assertThat(overview.allocations())
        .extracting(a -> a.percentage().intValue())
        .containsExactly(60, 30);
  }

  @Test
  void progressMayExceedOneHundredAndFlagsTheTargetAsReached() {
    Goal goal = save(GoalMother.goal().withTargetAmount("400"));
    UUID product = productWorth("Fund", "1000.00");
    allocate(goal, product, "50", productWorth("Other", "0.00"), "10");

    GoalProgress progress = query.list(false).get(0).progress();

    assertThat(progress.progressPercent()).isEqualByComparingTo("125.00");
    assertThat(progress.targetReached()).isTrue();
  }

  @Test
  void aGoalWithNoAllocationsHasAnEmptyListAndZeroProgress() {
    save(GoalMother.goal());

    GoalOverview overview = query.list(false).get(0);

    assertThat(overview.allocations()).isEmpty();
    assertThat(overview.progress().allocatedValue()).isEqualByComparingTo("0");
    assertThat(overview.progress().progressPercent()).isEqualByComparingTo("0");
  }

  @Test
  void archivedGoalsAreHiddenUnlessRequested() {
    Goal active = save(GoalMother.goal().withName("Active"));
    Goal achieved = save(GoalMother.goal().withName("Achieved").withStatus(GoalStatus.ACHIEVED));
    Goal archived = save(GoalMother.goal().withName("Archived").withStatus(GoalStatus.ARCHIVED));

    assertThat(query.list(false))
        .extracting(o -> o.goal().getId())
        .containsExactlyInAnyOrder(active.getId(), achieved.getId());
    assertThat(query.list(true))
        .extracting(o -> o.goal().getId())
        .containsExactlyInAnyOrder(active.getId(), achieved.getId(), archived.getId());
  }

  @Test
  void usesTheStandardOrder() {
    save(GoalMother.goal().withName("beta").dateless());
    save(GoalMother.goal().withName("Alpha").dateless());
    save(GoalMother.goal().withName("Later").withTargetDate(LocalDate.of(2031, 1, 1)));
    save(GoalMother.goal().withName("Sooner").withTargetDate(LocalDate.of(2027, 1, 1)));

    assertThat(query.list(false))
        .extracting(o -> o.goal().getName())
        .containsExactly("Sooner", "Later", "Alpha", "beta");
  }

  @Test
  void theListCarriesTheBaseScenarioStatusToo() {
    save(
        GoalMother.goal()
            .withTargetAmount("20000")
            .withTargetDate(LocalDate.of(2027, 11, 20))
            .withContribution("0")
            .withReturns("0", "0", "0"));

    assertThat(query.list(false).get(0).progress().projectedShortfall())
        .isEqualByComparingTo("20000.00");
  }

  @Test
  void getReturnsOneGoalEvenWhenArchivedAndAnUnknownIdIsNotFound() {
    Goal archived = save(GoalMother.goal().withStatus(GoalStatus.ARCHIVED));
    UUID product = productWorth("Fund", "100.00");
    allocate(archived, product, "10", productWorth("Other", "0.00"), "20");

    GoalOverview overview = query.get(archived.getId());

    assertThat(overview.goal().getStatus()).isEqualTo(GoalStatus.ARCHIVED);
    assertThat(overview.allocations()).hasSize(2);
    assertThatThrownBy(() -> query.get(UUID.randomUUID()))
        .isInstanceOf(GoalNotFoundException.class);
  }
}
