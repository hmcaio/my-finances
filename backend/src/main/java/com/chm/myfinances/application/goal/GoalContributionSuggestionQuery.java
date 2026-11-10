package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import com.chm.myfinances.application.investmentreport.SeriesPoint;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The planned-contribution helper (F041 spec, ADR 0035): the average of what was actually put into
 * the goal's allocated products over the last 12 <em>full</em> months before the current one (a
 * partial current month would drag an average of monthly buys down, like the emergency-fund
 * suggestion). Per month, {@code percentage / 100 x (contributed - opening-position costs)} summed
 * over the allocations, where {@code contributed} is the product's monthly buys minus sells from
 * {@link InvestmentValueSeriesQuery} and an opening position, which that series counts like a buy
 * in its as-of month (ADR 0033), is existing money rather than a monthly saving. Months before the
 * first non-zero month are skipped, a later gap counts as zero, and a negative average is returned
 * as zero (a planned contribution cannot be negative). The current split is applied to the past, as
 * in the goal history. Fills a form field and is never stored.
 */
@Service
public class GoalContributionSuggestionQuery {

  /** How many full months before the current one the average looks back over. */
  private static final int WINDOW_MONTHS = 12;

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final GoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentValueSeriesQuery valueSeriesQuery;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentOpeningPositionRepository openingPositionRepository;
  private final Clock clock;

  public GoalContributionSuggestionQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      InvestmentValueSeriesQuery valueSeriesQuery,
      InvestmentHoldingRepository holdingRepository,
      InvestmentOpeningPositionRepository openingPositionRepository,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.valueSeriesQuery = valueSeriesQuery;
    this.holdingRepository = holdingRepository;
    this.openingPositionRepository = openingPositionRepository;
    this.clock = clock;
  }

  /** The suggestion for the goal. 404 for an unknown goal. */
  public ContributionSuggestion suggest(UUID goalId) {
    Goal goal =
        goalRepository.findById(goalId).orElseThrow(() -> new GoalNotFoundException(goalId));
    YearMonth last = YearMonth.now(clock).minusMonths(1);
    YearMonth first = last.minusMonths(WINDOW_MONTHS - 1L);

    BigDecimal[] totals = new BigDecimal[WINDOW_MONTHS];
    Arrays.fill(totals, BigDecimal.ZERO);
    for (GoalAllocation allocation : allocationRepository.findByGoalId(goal.getId())) {
      addShare(totals, allocation, first, last);
    }

    int start = 0;
    while (start < totals.length && totals[start].signum() == 0) {
      start++;
    }
    int months = totals.length - start;
    if (months == 0) {
      return new ContributionSuggestion(BigDecimal.ZERO.setScale(2), 0);
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (int i = start; i < totals.length; i++) {
      sum = sum.add(totals[i]);
    }
    BigDecimal average =
        sum.divide(HUNDRED, 10, RoundingMode.HALF_UP)
            .divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
    return new ContributionSuggestion(
        average.signum() < 0 ? BigDecimal.ZERO.setScale(2) : average, months);
  }

  /**
   * Adds {@code percentage x (contributed - opening costs)} of the allocation's product to each
   * month of {@code totals}. Left unscaled by 100 so the sum stays exact until the average.
   */
  private void addShare(
      BigDecimal[] totals, GoalAllocation allocation, YearMonth first, YearMonth last) {
    List<SeriesPoint> points =
        valueSeriesQuery.series(first, last, allocation.getProductId()).get(0).points();
    List<UUID> holdingIds =
        holdingRepository.findByProductId(allocation.getProductId()).stream()
            .map(InvestmentHolding::getId)
            .toList();
    List<InvestmentOpeningPosition> positions =
        openingPositionRepository.findByHoldingIds(holdingIds);
    for (int i = 0; i < totals.length; i++) {
      YearMonth month = first.plusMonths(i);
      BigDecimal net = points.get(i).contributed();
      for (InvestmentOpeningPosition position : positions) {
        if (YearMonth.from(position.getAsOfDate()).equals(month)) {
          net = net.subtract(position.amountContributed());
        }
      }
      totals[i] = totals[i].add(net.multiply(allocation.getPercentage()));
    }
  }
}
