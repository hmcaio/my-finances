package com.chm.myfinances.application.goal;

import com.chm.myfinances.domain.goal.Goal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects one {@link Goal} under one scenario (F039 spec, ADR 0034) from the value it holds now.
 * Pure: the callers ({@link GoalProjectionQuery} for the detail page, {@link GoalSummaries} for the
 * list and the dashboard) read the goal's value however they like and hand it in.
 *
 * <p>A dated goal runs from the current month through the target month ({@code N} whole months,
 * zero when the target month is not in the future, which reads as a plain shortfall). A dateless
 * goal is simulated up to {@link GoalProjectionMath#MAX_MONTHS} and reports the first month it
 * reaches the target.
 */
final class GoalScenarioCalculator {

  private GoalScenarioCalculator() {}

  static ScenarioProjection scenario(
      ProjectionScenario which, Goal goal, BigDecimal allocatedValue, LocalDate today) {
    BigDecimal annualReturn = annualReturn(which, goal);
    double start = allocatedValue.doubleValue();
    double rate = GoalProjectionMath.monthlyRate(annualReturn);
    double contribution = goal.getPlannedMonthlyContribution().doubleValue();
    YearMonth first = YearMonth.from(today);
    BigDecimal target = goal.getTargetAmount();

    if (goal.getTargetDate() != null) {
      int months = monthsUntil(first, YearMonth.from(goal.getTargetDate()));
      double[] values = GoalProjectionMath.simulate(start, rate, contribution, months);
      BigDecimal projected = GoalProjectionMath.money(values[months]);
      return new ScenarioProjection(
          which,
          annualReturn,
          points(first, values, months),
          projected,
          target.subtract(projected),
          GoalProjectionMath.requiredContribution(start, rate, months, target.doubleValue()),
          null);
    }

    double[] values =
        GoalProjectionMath.simulate(start, rate, contribution, GoalProjectionMath.MAX_MONTHS);
    Integer monthsToTarget = null;
    for (int m = 0; m <= GoalProjectionMath.MAX_MONTHS; m++) {
      if (GoalProjectionMath.money(values[m]).compareTo(target) >= 0) {
        monthsToTarget = m;
        break;
      }
    }
    return new ScenarioProjection(
        which,
        annualReturn,
        points(first, values, GoalProjectionMath.MAX_MONTHS),
        null,
        null,
        null,
        monthsToTarget);
  }

  /** A dateless goal's points all stop where the pessimistic scenario reaches the target. */
  static List<ScenarioProjection> trimToPessimisticHorizon(List<ScenarioProjection> scenarios) {
    Integer pessimisticMonths = scenarios.get(0).monthsToTarget();
    int horizon = pessimisticMonths == null ? GoalProjectionMath.MAX_MONTHS : pessimisticMonths;
    return scenarios.stream()
        .map(
            s ->
                new ScenarioProjection(
                    s.scenario(),
                    s.annualReturn(),
                    s.points().subList(0, horizon + 1),
                    s.projectedValueAtTargetDate(),
                    s.shortfallAtTargetDate(),
                    s.requiredMonthlyContribution(),
                    s.monthsToTarget()))
        .toList();
  }

  private static BigDecimal annualReturn(ProjectionScenario which, Goal goal) {
    return switch (which) {
      case PESSIMISTIC -> goal.getReturnPessimistic();
      case BASE -> goal.getReturnBase();
      case OPTIMISTIC -> goal.getReturnOptimistic();
    };
  }

  private static int monthsUntil(YearMonth from, YearMonth to) {
    return (int) Math.max(0, from.until(to, ChronoUnit.MONTHS));
  }

  private static List<ProjectionPoint> points(YearMonth first, double[] values, int months) {
    List<ProjectionPoint> points = new ArrayList<>(months + 1);
    for (int m = 0; m <= months; m++) {
      points.add(new ProjectionPoint(first.plusMonths(m), GoalProjectionMath.money(values[m])));
    }
    return List.copyOf(points);
  }
}
