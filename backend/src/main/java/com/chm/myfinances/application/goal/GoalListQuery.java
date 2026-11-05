package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * The goals list and the goal detail (F039 spec, ADR 0034): each goal with its allocations (product
 * name and percentage), allocated value, progress and base-scenario status ({@link GoalOverview}).
 * The list is in the standard order ({@link GoalSummaries#STANDARD_ORDER}) and hides archived goals
 * unless asked. Reads the allocations, the product values and the product names once for all the
 * goals, never per goal.
 */
@Service
public class GoalListQuery {

  private final GoalRepository goalRepository;
  private final GoalAllocationRepository allocationRepository;
  private final InvestmentProductRepository productRepository;
  private final GoalSummaries summaries;
  private final Clock clock;

  public GoalListQuery(
      GoalRepository goalRepository,
      GoalAllocationRepository allocationRepository,
      InvestmentProductRepository productRepository,
      LatestInvestmentSnapshotQuery snapshotQuery,
      Clock clock) {
    this.goalRepository = goalRepository;
    this.allocationRepository = allocationRepository;
    this.productRepository = productRepository;
    this.summaries = new GoalSummaries(allocationRepository, snapshotQuery);
    this.clock = clock;
  }

  public List<GoalOverview> list(boolean includeArchived) {
    List<Goal> goals =
        goalRepository.findAll().stream()
            .filter(goal -> includeArchived || goal.getStatus() != GoalStatus.ARCHIVED)
            .sorted(GoalSummaries.STANDARD_ORDER)
            .toList();
    return overviews(goals);
  }

  /** One goal, archived or not. 404 for an unknown goal. */
  public GoalOverview get(UUID goalId) {
    Goal goal =
        goalRepository.findById(goalId).orElseThrow(() -> new GoalNotFoundException(goalId));
    return overviews(List.of(goal)).get(0);
  }

  private List<GoalOverview> overviews(List<Goal> goals) {
    if (goals.isEmpty()) {
      return List.of();
    }
    List<UUID> goalIds = goals.stream().map(Goal::getId).toList();
    Map<UUID, GoalProgress> progress = summaries.progressOf(goals, LocalDate.now(clock));
    Map<UUID, List<GoalAllocation>> allocationsByGoal =
        allocationRepository.findByGoalIds(goalIds).stream()
            .collect(Collectors.groupingBy(GoalAllocation::getGoalId));
    Map<UUID, String> productNames = new HashMap<>();
    for (InvestmentProduct product : productRepository.findAll()) {
      productNames.put(product.getId(), product.getName());
    }
    return goals.stream()
        .map(
            goal ->
                new GoalOverview(
                    goal,
                    progress.get(goal.getId()),
                    allocationsByGoal.getOrDefault(goal.getId(), List.of()).stream()
                        .sorted(
                            Comparator.comparing(GoalAllocation::getPercentage)
                                .reversed()
                                .thenComparing(a -> a.getProductId().toString()))
                        .map(
                            a ->
                                new GoalOverview.AllocationDetail(
                                    a.getProductId(),
                                    productNames.get(a.getProductId()),
                                    a.getPercentage()))
                        .toList()))
        .toList();
  }
}
