package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link GoalContributionSuggestionQuery} (F041 spec, ADR 0035),
 * written first (ADR 0004): the goal's share of what was put into its products, averaged over the
 * last 12 full months before the current one, starting at the first month with a contribution, with
 * opening-position costs removed and the result floored at zero. Fakes only; today is 2026-11-05,
 * so the window is 2025-11 through 2026-10.
 */
class GoalContributionSuggestionQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  private final FakeGoalRepository goalRepository = new FakeGoalRepository();
  private final FakeGoalAllocationRepository allocationRepository =
      new FakeGoalAllocationRepository(goalRepository);
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final FakeInvestmentOpeningPositionRepository openingPositionRepository =
      new FakeInvestmentOpeningPositionRepository();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final GoalContributionSuggestionQuery query =
      new GoalContributionSuggestionQuery(
          goalRepository,
          allocationRepository,
          new InvestmentValueSeriesQuery(
              productRepository,
              holdingRepository,
              new FakeInvestmentSnapshotRepository(),
              tradeLineRepository,
              openingPositionRepository,
              clock),
          holdingRepository,
          openingPositionRepository,
          clock);

  private UUID newProduct(String name) {
    InvestmentProduct product =
        productRepository.save(InvestmentProductMother.product().withName(name).build());
    holdingRepository.save(
        InvestmentHoldingMother.holding().withProductId(product.getId()).build());
    return product.getId();
  }

  private void trade(UUID productId, TradeSide side, LocalDate date, String amount) {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            date,
            UUID.randomUUID(),
            UUID.randomUUID(),
            productId,
            side,
            BigDecimal.ONE,
            new BigDecimal(amount),
            BigDecimal.ZERO));
  }

  private void buy(UUID productId, LocalDate date, String amount) {
    trade(productId, TradeSide.BUY, date, amount);
  }

  private void openingPosition(UUID productId, LocalDate asOf, String quantity, String price) {
    InvestmentHolding holding = holdingRepository.findByProductId(productId).get(0);
    openingPositionRepository.save(
        InvestmentOpeningPosition.create(
            UUID.randomUUID(),
            holding.getId(),
            asOf,
            new BigDecimal(quantity),
            new BigDecimal(price)));
  }

  /** A goal allocating {@code share} percent of each {@code productId}. */
  private Goal goalAllocating(String share, UUID... productIds) {
    Goal goal = goalRepository.save(GoalMother.goal().build());
    List<GoalAllocation> allocations = new ArrayList<>();
    for (UUID productId : productIds) {
      allocations.add(
          GoalAllocation.create(UUID.randomUUID(), goal.getId(), productId, new BigDecimal(share)));
    }
    allocationRepository.replaceForGoal(goal.getId(), allocations);
    return goal;
  }

  @Test
  void averagesTheLastTwelveFullMonthsAndExcludesTheCurrentOne() {
    UUID a = newProduct("A");
    for (int month = 0; month < 12; month++) {
      // 2025-11 through 2026-10, 500 each.
      buy(a, LocalDate.of(2025, 11, 10).plusMonths(month), "500.00");
    }
    buy(a, LocalDate.of(2026, 11, 2), "99999.00"); // the current month is not a full month
    buy(a, LocalDate.of(2025, 10, 20), "99999.00"); // thirteen months back is out of the window
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    assertThat(suggestion.monthsAveraged()).isEqualTo(12);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("500.00");
  }

  @Test
  void startsAtTheFirstMonthWithAContributionButCountsLaterGapsAsZero() {
    UUID a = newProduct("A");
    buy(a, LocalDate.of(2026, 8, 10), "600.00");
    // September has none: it counts as zero, since it comes after the first contribution.
    buy(a, LocalDate.of(2026, 10, 10), "300.00");
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    // 900 over August..October.
    assertThat(suggestion.monthsAveraged()).isEqualTo(3);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("300.00");
  }

  @Test
  void multipliesEachProductsContributionByTheGoalsPercentage() {
    UUID a = newProduct("A");
    UUID b = newProduct("B");
    for (LocalDate date : List.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 10, 10))) {
      buy(a, date, "1000.00");
      buy(b, date, "400.00");
    }
    Goal goal = goalRepository.save(GoalMother.goal().build());
    allocationRepository.replaceForGoal(
        goal.getId(),
        List.of(
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), a, new BigDecimal("50")),
            GoalAllocation.create(UUID.randomUUID(), goal.getId(), b, new BigDecimal("25"))));

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    // 0.5 x 1000 + 0.25 x 400 = 600 a month.
    assertThat(suggestion.monthsAveraged()).isEqualTo(2);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("600.00");
  }

  @Test
  void anOpeningPositionsCostIsRemovedInItsAsOfMonth() {
    UUID a = newProduct("A");
    // 10 cotas at 500 recorded as of 2026-08: 5000 lands in August's contributed, but it is
    // existing money, not a saving. August nets zero, so it is a leading month and is skipped.
    openingPosition(a, LocalDate.of(2026, 8, 10), "10", "500");
    buy(a, LocalDate.of(2026, 9, 10), "300.00");
    buy(a, LocalDate.of(2026, 10, 10), "300.00");
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    assertThat(suggestion.monthsAveraged()).isEqualTo(2);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("300.00");
  }

  @Test
  void aBuyInTheSameMonthAsTheOpeningPositionStillCounts() {
    UUID a = newProduct("A");
    openingPosition(a, LocalDate.of(2026, 9, 1), "10", "500");
    buy(a, LocalDate.of(2026, 9, 15), "200.00");
    buy(a, LocalDate.of(2026, 10, 15), "400.00");
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    assertThat(suggestion.monthsAveraged()).isEqualTo(2);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("300.00");
  }

  @Test
  void aNetSellAverageIsReturnedAsZero() {
    UUID a = newProduct("A");
    trade(a, TradeSide.SELL, LocalDate.of(2026, 9, 10), "1000.00");
    buy(a, LocalDate.of(2026, 10, 10), "200.00");
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    // (-1000 + 200) / 2 is negative; a planned contribution cannot be.
    assertThat(suggestion.monthsAveraged()).isEqualTo(2);
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("0.00");
  }

  @Test
  void theAverageIsRoundedToCents() {
    UUID a = newProduct("A");
    buy(a, LocalDate.of(2026, 8, 10), "100.00");
    buy(a, LocalDate.of(2026, 9, 10), "0.01");
    buy(a, LocalDate.of(2026, 10, 10), "0.01");
    Goal goal = goalAllocating("100", a);

    // 100.02 / 3 = 33.34.
    assertThat(query.suggest(goal.getId()).averageMonthlyContribution())
        .isEqualByComparingTo("33.34");
  }

  @Test
  void aGoalWithNothingAllocatedReturnsZeroAndZero() {
    Goal goal = goalRepository.save(GoalMother.goal().build());

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    assertThat(suggestion.monthsAveraged()).isZero();
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("0.00");
  }

  @Test
  void allocatedProductsWithNoContributionReturnZeroAndZero() {
    UUID a = newProduct("A");
    Goal goal = goalAllocating("100", a);

    ContributionSuggestion suggestion = query.suggest(goal.getId());

    assertThat(suggestion.monthsAveraged()).isZero();
    assertThat(suggestion.averageMonthlyContribution()).isEqualByComparingTo("0.00");
  }

  @Test
  void anUnknownGoalIsNotFound() {
    assertThatThrownBy(() -> query.suggest(UUID.randomUUID()))
        .isInstanceOf(GoalNotFoundException.class);
  }
}
