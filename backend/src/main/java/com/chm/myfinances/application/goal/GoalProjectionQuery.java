package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A goal's deterministic projection under three scenarios (F039 spec, ADR 0034): computed on read,
 * never stored, from user-entered assumptions only (PRD S3). Starts from the goal's current value
 * ({@link GoalValuation}) and compounds it monthly at each scenario's real annual return with the
 * planned contribution added at month end ({@link GoalProjectionMath}); everything is in today's
 * money. "Now" comes from the injected {@code Clock}.
 *
 * <p>A dated goal runs from the current month through the target month ({@code N} whole months,
 * zero when the target month is not in the future, which reads as a plain shortfall) and reports
 * the projected value, the shortfall against the target and the contribution required. A dateless
 * goal (an emergency fund) is simulated up to 600 months: it reports the first month the value
 * reaches the target, and its points run until the pessimistic scenario gets there.
 */
@Service
public class GoalProjectionQuery {

  private final GoalRepository goalRepository;
  private final GoalValuation valuation;
  private final Clock clock;

  public GoalProjectionQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      LatestInvestmentSnapshotQuery snapshotQuery,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.valuation = new GoalValuation(allocationRepository, snapshotQuery);
    this.clock = clock;
  }

  /** The goal's projection. 404 for an unknown goal. */
  public GoalProjection projection(UUID goalId) {
    Goal goal =
        goalRepository.findById(goalId).orElseThrow(() -> new GoalNotFoundException(goalId));
    LocalDate today = LocalDate.now(clock);
    GoalValuation.GoalValue value = valuation.valueOf(goalId, today);
    List<ScenarioProjection> scenarios =
        List.of(
            scenario(
                ProjectionScenario.PESSIMISTIC, goal.getReturnPessimistic(), goal, value, today),
            scenario(ProjectionScenario.BASE, goal.getReturnBase(), goal, value, today),
            scenario(
                ProjectionScenario.OPTIMISTIC, goal.getReturnOptimistic(), goal, value, today));
    if (goal.getTargetDate() == null) {
      scenarios = trimToPessimisticHorizon(scenarios);
    }
    return new GoalProjection(value.allocatedValue(), value.latestSnapshotDate(), scenarios);
  }

  private ScenarioProjection scenario(
      ProjectionScenario which,
      BigDecimal annualReturn,
      Goal goal,
      GoalValuation.GoalValue value,
      LocalDate today) {
    double start = value.allocatedValue().doubleValue();
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
  private static List<ScenarioProjection> trimToPessimisticHorizon(
      List<ScenarioProjection> scenarios) {
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
