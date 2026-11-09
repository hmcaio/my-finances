package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The goals card of the dashboard (F039 spec, PRD S6.8, ADR 0034): every goal flagged "show on
 * dashboard", in the standard order, each with its base-scenario status. One call serves the whole
 * card - any number of goals can be flagged, so it must not mean a projection call per goal ({@link
 * GoalSummaries}). An archived goal never appears: archiving clears the flag in the domain, and the
 * status is checked here too, so a row that somehow kept the flag still stays off the card.
 */
@Service
public class GoalDashboardQuery {

  private final GoalRepository goalRepository;
  private final GoalSummaries summaries;
  private final Clock clock;

  public GoalDashboardQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      LatestInvestmentSnapshotQuery snapshotQuery,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.summaries = new GoalSummaries(allocationRepository, snapshotQuery);
    this.clock = clock;
  }

  public List<GoalSummary> dashboard() {
    List<Goal> flagged =
        goalRepository.findAll().stream()
            .filter(Goal::isShowOnDashboard)
            .filter(goal -> goal.getStatus() != GoalStatus.ARCHIVED)
            .sorted(GoalSummaries.STANDARD_ORDER)
            .toList();
    if (flagged.isEmpty()) {
      return List.of();
    }
    Map<UUID, GoalProgress> progress = summaries.progressOf(flagged, LocalDate.now(clock));
    return flagged.stream().map(goal -> new GoalSummary(goal, progress.get(goal.getId()))).toList();
  }
}
