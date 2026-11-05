package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The shared read behind the goal list and the dashboard (F039 spec, ADR 0034): the standard order
 * and, for many goals at once, each one's {@link GoalProgress}. Values come from {@link
 * GoalValuation} in two passes whatever the number of goals, so neither screen runs a projection
 * call or a repository read per goal.
 */
final class GoalSummaries {

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  /**
   * Dated goals first by target date ascending, then dateless goals by name ignoring case; ties by
   * id (F039 spec: no priority field, ADR 0034).
   */
  static final Comparator<Goal> STANDARD_ORDER =
      Comparator.comparing((Goal goal) -> goal.getTargetDate() == null)
          .thenComparing(Goal::getTargetDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(
              goal -> goal.getTargetDate() == null ? goal.getName() : "",
              String.CASE_INSENSITIVE_ORDER)
          .thenComparing(Goal::getId);

  private final GoalValuation valuation;

  GoalSummaries(
      GoalAllocationRepository allocationRepository, LatestInvestmentSnapshotQuery snapshotQuery) {
    this.valuation = new GoalValuation(allocationRepository, snapshotQuery);
  }

  /** The progress of each goal, keyed by goal id. */
  Map<UUID, GoalProgress> progressOf(List<Goal> goals, LocalDate today) {
    Map<UUID, GoalValuation.GoalValue> values =
        valuation.valuesByGoal(goals.stream().map(Goal::getId).toList(), today);
    Map<UUID, GoalProgress> progress = new HashMap<>();
    for (Goal goal : goals) {
      BigDecimal allocated = values.get(goal.getId()).allocatedValue();
      ScenarioProjection base =
          GoalScenarioCalculator.scenario(ProjectionScenario.BASE, goal, allocated, today);
      progress.put(
          goal.getId(),
          new GoalProgress(
              allocated,
              allocated.multiply(HUNDRED).divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP),
              allocated.compareTo(goal.getTargetAmount()) >= 0,
              base.shortfallAtTargetDate(),
              base.monthsToTarget()));
    }
    return progress;
  }
}
