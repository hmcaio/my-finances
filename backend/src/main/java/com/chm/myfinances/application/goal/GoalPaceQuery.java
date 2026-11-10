package com.chm.myfinances.application.goal;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A goal's current-pace line (F041 spec, ADR 0035): the average monthly increase of the goal's
 * value over the last 12 months of {@link GoalHistoryQuery} (the window ends in the current month),
 * drawn as a straight line from the value now. Months before the first non-zero value are skipped,
 * and so is everything up to the latest opening position among the holdings of the goal's allocated
 * products: an opening position lands existing money as a value jump in its as-of month (ADR 0033),
 * which is not growth. With fewer than {@link GoalProjectionMath#MIN_PACE_POINTS} months left there
 * is no pace.
 *
 * <p>Computed on read, never stored, and nominal (recorded money, not deflated); the current split
 * is applied to the past, like the history it reads.
 */
@Service
public class GoalPaceQuery {

  /** The months of history the pace looks at, the current one included. */
  static final int WINDOW_MONTHS = 12;

  private final GoalHistoryQuery historyQuery;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentOpeningPositionRepository openingPositionRepository;

  public GoalPaceQuery(
      GoalHistoryQuery historyQuery,
      GoalAllocationRepository allocationRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentOpeningPositionRepository openingPositionRepository) {
    this.historyQuery = historyQuery;
    this.allocationRepository = allocationRepository;
    this.holdingRepository = holdingRepository;
    this.openingPositionRepository = openingPositionRepository;
  }

  /**
   * The pace line for {@code goal}, or empty when the history is too short. {@code allocatedValue}
   * is the goal's value now (the scenarios' starting value) and {@code horizonMonths} how far the
   * scenarios run, so the line never outgrows them.
   */
  public Optional<PaceProjection> pace(
      Goal goal, BigDecimal allocatedValue, int horizonMonths, LocalDate today) {
    List<GoalAllocation> allocations = allocationRepository.findByGoalId(goal.getId());
    if (allocations.isEmpty()) {
      return Optional.empty();
    }
    YearMonth current = YearMonth.from(today);
    YearMonth windowStart = current.minusMonths(WINDOW_MONTHS - 1L);
    List<ProjectionPoint> history = historyQuery.history(goal.getId(), windowStart, current);

    int start = firstNonZero(history);
    if (start < 0) {
      return Optional.empty();
    }
    start = Math.max(start, afterLatestOpeningPosition(allocations, windowStart));

    List<BigDecimal> values = new ArrayList<>();
    for (ProjectionPoint point : history.subList(Math.min(start, history.size()), history.size())) {
      values.add(point.value());
    }
    if (!values.isEmpty()) {
      // One "now" for every line: the goal's value, not the last history point.
      values.set(values.size() - 1, allocatedValue);
    }
    return GoalProjectionMath.monthlyPace(values)
        .map(pace -> line(goal, allocatedValue, pace, horizonMonths, current));
  }

  private PaceProjection line(
      Goal goal, BigDecimal allocatedValue, BigDecimal pace, int horizonMonths, YearMonth current) {
    List<ProjectionPoint> points = new ArrayList<>(horizonMonths + 1);
    for (int i = 0; i <= horizonMonths; i++) {
      points.add(
          new ProjectionPoint(
              current.plusMonths(i), allocatedValue.add(pace.multiply(BigDecimal.valueOf(i)))));
    }
    BigDecimal target = goal.getTargetAmount();
    if (goal.getTargetDate() != null) {
      BigDecimal projected = points.get(points.size() - 1).value();
      return new PaceProjection(
          pace, List.copyOf(points), projected, target.subtract(projected), null);
    }
    return new PaceProjection(
        pace,
        List.copyOf(points),
        null,
        null,
        GoalProjectionMath.paceMonthsToTarget(allocatedValue, pace, target));
  }

  /** The index of the first month with a value, {@code -1} when every month is zero. */
  private static int firstNonZero(List<ProjectionPoint> history) {
    for (int i = 0; i < history.size(); i++) {
      if (history.get(i).value().signum() != 0) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The window index just after the latest opening position's as-of month among the allocated
   * products' holdings ({@code 0} when there is none or it is before the window).
   */
  private int afterLatestOpeningPosition(List<GoalAllocation> allocations, YearMonth windowStart) {
    List<UUID> holdingIds = new ArrayList<>();
    for (GoalAllocation allocation : allocations) {
      for (InvestmentHolding holding :
          holdingRepository.findByProductId(allocation.getProductId())) {
        holdingIds.add(holding.getId());
      }
    }
    YearMonth latest = null;
    for (InvestmentOpeningPosition position :
        openingPositionRepository.findByHoldingIds(holdingIds)) {
      YearMonth asOf = YearMonth.from(position.getAsOfDate());
      if (latest == null || asOf.isAfter(latest)) {
        latest = asOf;
      }
    }
    if (latest == null) {
      return 0;
    }
    return (int) Math.max(0, windowStart.until(latest, ChronoUnit.MONTHS) + 1);
  }
}
