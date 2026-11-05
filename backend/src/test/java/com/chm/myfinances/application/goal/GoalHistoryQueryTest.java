package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentreport.InvalidValueSeriesRangeException;
import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalHistoryQuery} (F039, ADR 0034), written first (ADR 0004):
 * per month, {@code percentage / 100 x the product's value} summed over the goal's allocations,
 * using the <em>current</em> split retroactively. Fakes only, fixed {@code Clock} (today is
 * 2026-11-05).
 */
class GoalHistoryQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);
  private static final YearMonth AUG = YearMonth.of(2026, 8);
  private static final YearMonth OCT = YearMonth.of(2026, 10);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final GoalHistoryQuery query =
      new GoalHistoryQuery(
          goalRepository,
          allocationRepository,
          new InvestmentValueSeriesQuery(
              productRepository,
              holdingRepository,
              snapshotRepository,
              new FakeTransferTradeLineRepository(),
              new FakeInvestmentOpeningPositionRepository(),
              clock),
          clock);

  private UUID newProduct(String name) {
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName(name).build());
    holdingRepository.save(
        InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    return product.getId();
  }

  private void snapshot(UUID productId, LocalDate date, String balance) {
    InvestmentHolding holding = holdingRepository.findByProductId(productId).get(0);
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), date, new BigDecimal(balance)));
  }

  private Goal goalSplit(UUID first, String firstPct, UUID second, String secondPct) {
    Goal goal = goalRepository.save(GoalMother.goal().build());
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), first, new BigDecimal(firstPct)),
            GoalAllocation.create(
                UUID.randomUUID(), goal.getId(), second, new BigDecimal(secondPct))));
    return goal;
  }

  @Test
  void sumsEachMonthsShareOfEveryAllocatedProductsSeries() {
    UUID a = newProduct("A");
    UUID b = newProduct("B");
    snapshot(a, LocalDate.of(2026, 8, 20), "1000.00");
    snapshot(a, LocalDate.of(2026, 9, 20), "1100.00");
    snapshot(a, LocalDate.of(2026, 10, 20), "1200.00");
    snapshot(b, LocalDate.of(2026, 8, 10), "500.00");
    snapshot(b, LocalDate.of(2026, 10, 10), "800.00");
    Goal goal = goalSplit(a, "60", b, "40");

    List<ProjectionPoint> history = query.history(goal.getId(), AUG, OCT);

    assertThat(history)
        .extracting(ProjectionPoint::month)
        .containsExactly(AUG, YearMonth.of(2026, 9), OCT);
    // Aug: 0.6 x 1000 + 0.4 x 500; Sep: 0.6 x 1100 + 0.4 x 500 (B carries over); Oct: 0.6 x 1200 +
    // 0.4 x 800.
    assertThat(history.get(0).value()).isEqualByComparingTo("800.00");
    assertThat(history.get(1).value()).isEqualByComparingTo("860.00");
    assertThat(history.get(2).value()).isEqualByComparingTo("1040.00");
  }

  @Test
  void aMonthBeforeAProductsFirstSnapshotCountsAsZeroForThatProduct() {
    UUID a = newProduct("A");
    UUID b = newProduct("B");
    snapshot(a, LocalDate.of(2026, 8, 20), "1000.00");
    snapshot(b, LocalDate.of(2026, 10, 10), "800.00");
    Goal goal = goalSplit(a, "50", b, "50");

    List<ProjectionPoint> history = query.history(goal.getId(), AUG, OCT);

    assertThat(history.get(0).value()).isEqualByComparingTo("500.00");
    assertThat(history.get(2).value()).isEqualByComparingTo("900.00");
  }

  @Test
  void roundsEachMonthToCents() {
    UUID a = newProduct("A");
    snapshot(a, LocalDate.of(2026, 8, 20), "100.00");
    Goal goal = goalRepository.save(GoalMother.goal().build());
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), a, new BigDecimal("33.33"))));

    assertThat(query.history(goal.getId(), AUG, AUG).get(0).value()).isEqualByComparingTo("33.33");
  }

  @Test
  void aGoalWithNoAllocationsYieldsZeroValuedPointsForEveryMonth() {
    Goal goal = goalRepository.save(GoalMother.goal().build());

    List<ProjectionPoint> history = query.history(goal.getId(), AUG, OCT);

    assertThat(history).hasSize(3);
    assertThat(history).allSatisfy(p -> assertThat(p.value()).isEqualByComparingTo("0"));
  }

  @Test
  void theRangeIsClampedToTheCurrentMonth() {
    Goal goal = goalRepository.save(GoalMother.goal().build());

    List<ProjectionPoint> history = query.history(goal.getId(), OCT, YearMonth.of(2027, 3));

    assertThat(history)
        .extracting(ProjectionPoint::month)
        .containsExactly(OCT, YearMonth.of(2026, 11));
  }

  @Test
  void aReversedRangeIsRejectedEvenWithNothingAllocated() {
    Goal goal = goalRepository.save(GoalMother.goal().build());

    assertThatThrownBy(() -> query.history(goal.getId(), OCT, AUG))
        .isInstanceOf(InvalidValueSeriesRangeException.class);
  }

  @Test
  void aRangeOfTenYearsOrMoreIsRejected() {
    Goal goal = goalRepository.save(GoalMother.goal().build());

    assertThatThrownBy(() -> query.history(goal.getId(), YearMonth.of(2016, 1), OCT))
        .isInstanceOf(InvalidValueSeriesRangeException.class);
  }

  @Test
  void anUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> query.history(UUID.randomUUID(), AUG, OCT))
        .isInstanceOf(GoalNotFoundException.class);
  }
}
