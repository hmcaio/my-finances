package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * A goal's deterministic projection under three scenarios (F039 spec, ADR 0034): computed on read,
 * never stored, from user-entered assumptions only (PRD S3). Starts from the goal's current value
 * ({@link GoalValuation}) and compounds it monthly at each scenario's real annual return with the
 * planned contribution added at month end ({@link GoalProjectionMath}, {@link
 * GoalScenarioCalculator}); everything is in today's money. "Now" comes from the injected {@code
 * Clock}.
 *
 * <p>A dated goal reports the projected value at the target date, the shortfall against the target
 * and the contribution required; a dateless goal (an emergency fund) reports the first month the
 * value reaches the target, and its points run until the pessimistic scenario gets there.
 *
 * <p>The projection also carries the current-pace line ({@link GoalPaceQuery}, F041): derived from
 * recorded history, not from assumptions, and {@code null} when there is too little of it.
 */
@Service
public class GoalProjectionQuery {

  private final GoalRepository goalRepository;
  private final GoalValuation valuation;
  private final GoalPaceQuery paceQuery;
  private final Clock clock;

  public GoalProjectionQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      LatestInvestmentSnapshotQuery snapshotQuery,
      GoalPaceQuery paceQuery,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.valuation = new GoalValuation(allocationRepository, snapshotQuery);
    this.paceQuery = paceQuery;
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
            GoalScenarioCalculator.scenario(
                ProjectionScenario.PESSIMISTIC, goal, value.allocatedValue(), today),
            GoalScenarioCalculator.scenario(
                ProjectionScenario.BASE, goal, value.allocatedValue(), today),
            GoalScenarioCalculator.scenario(
                ProjectionScenario.OPTIMISTIC, goal, value.allocatedValue(), today));
    if (goal.getTargetDate() == null) {
      scenarios = GoalScenarioCalculator.trimToPessimisticHorizon(scenarios);
    }
    // The pace runs as far as the scenarios do (F041, ADR 0035), never past them.
    int horizonMonths = scenarios.get(0).points().size() - 1;
    PaceProjection pace =
        paceQuery.pace(goal, value.allocatedValue(), horizonMonths, today).orElse(null);
    return new GoalProjection(value.allocatedValue(), value.latestSnapshotDate(), scenarios, pace);
  }
}
