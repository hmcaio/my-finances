package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import com.chm.myfinances.application.investmentreport.ProductSeries;
import com.chm.myfinances.application.investmentreport.SeriesPoint;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A goal's progress history (F039 spec, ADR 0034): for each month, {@code percentage / 100 x the
 * product's value} summed over the goal's allocations, from {@link InvestmentValueSeriesQuery}'s
 * per-product series. It applies the <em>current</em> split to the past - splits are edited in
 * place and not stored - which is why the UI labels it "as of the current allocation". A month
 * before a product's first snapshot counts as zero for that product; months after the current one
 * are not included (the series query clamps the range, and so does this one, so a goal with nothing
 * allocated still returns one zero-valued point per month).
 */
@Service
public class GoalHistoryQuery {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final GoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentValueSeriesQuery valueSeriesQuery;
  private final Clock clock;

  public GoalHistoryQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      InvestmentValueSeriesQuery valueSeriesQuery,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.valueSeriesQuery = valueSeriesQuery;
    this.clock = clock;
  }

  /**
   * The goal's monthly values from {@code from} to {@code to} inclusive (clamped to the current
   * month). 404 for an unknown goal; 400 {@code InvalidValueSeriesRangeException} for a reversed or
   * over-long range.
   */
  public List<ProjectionPoint> history(UUID goalId, YearMonth from, YearMonth to) {
    goalRepository.findById(goalId).orElseThrow(() -> new GoalNotFoundException(goalId));
    InvestmentValueSeriesQuery.requireValidRange(from, to);
    YearMonth current = YearMonth.now(clock);
    YearMonth last = to.isAfter(current) ? current : to;

    BigDecimal[] totals = new BigDecimal[monthsBetween(from, last)];
    Arrays.fill(totals, BigDecimal.ZERO);
    for (GoalAllocation allocation : allocationRepository.findByGoalId(goalId)) {
      List<ProductSeries> series = valueSeriesQuery.series(from, to, allocation.getProductId());
      List<SeriesPoint> points = series.get(0).points();
      for (int i = 0; i < totals.length; i++) {
        BigDecimal value = points.get(i).value();
        if (value != null) {
          totals[i] = totals[i].add(value.multiply(allocation.getPercentage()));
        }
      }
    }

    List<ProjectionPoint> history = new ArrayList<>(totals.length);
    for (int i = 0; i < totals.length; i++) {
      history.add(
          new ProjectionPoint(
              from.plusMonths(i), totals[i].divide(HUNDRED, 2, RoundingMode.HALF_UP)));
    }
    return List.copyOf(history);
  }

  /**
   * The number of months from {@code from} through {@code last}, both inclusive (0 if reversed).
   */
  private static int monthsBetween(YearMonth from, YearMonth last) {
    return last.isBefore(from) ? 0 : (int) from.until(last, ChronoUnit.MONTHS) + 1;
  }
}
