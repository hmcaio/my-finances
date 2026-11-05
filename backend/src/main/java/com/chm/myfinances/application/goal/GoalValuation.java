package com.chm.myfinances.application.goal;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.application.investmentsnapshot.ProductSnapshotValue;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The current value of goals (F039 spec, ADR 0034): a goal's value is the sum of {@code percentage
 * / 100 x the product's value} over its allocations, where a product's value is the sum of its
 * holdings' latest snapshots as of the date (the existing definition, {@link
 * LatestInvestmentSnapshotQuery#valueByProduct}; no new valuation). A product with no snapshot
 * counts as zero. Reads all the allocations and all the product values in two passes whatever the
 * number of goals, so the list, the dashboard and the projection never loop a read per goal.
 */
final class GoalValuation {

  /** A goal's value now and the most recent snapshot date behind it ({@code null} if none). */
  record GoalValue(BigDecimal allocatedValue, LocalDate latestSnapshotDate) {}

  private static final BigDecimal HUNDRED = new BigDecimal("100");

  private final GoalAllocationRepository allocationRepository;
  private final LatestInvestmentSnapshotQuery snapshotQuery;

  GoalValuation(
      GoalAllocationRepository allocationRepository, LatestInvestmentSnapshotQuery snapshotQuery) {
    this.allocationRepository = allocationRepository;
    this.snapshotQuery = snapshotQuery;
  }

  /** The value of every goal in {@code goalIds} (a goal with no allocation is worth zero). */
  Map<UUID, GoalValue> valuesByGoal(Collection<UUID> goalIds, LocalDate asOf) {
    Map<UUID, ProductSnapshotValue> productValues = snapshotQuery.valueByProduct(asOf);
    Map<UUID, BigDecimal> sums = new HashMap<>();
    Map<UUID, LocalDate> dates = new HashMap<>();
    for (GoalAllocation allocation : allocationRepository.findByGoalIds(goalIds)) {
      ProductSnapshotValue product = productValues.get(allocation.getProductId());
      if (product == null) {
        continue;
      }
      sums.merge(
          allocation.getGoalId(),
          product.value().multiply(allocation.getPercentage()),
          BigDecimal::add);
      dates.merge(
          allocation.getGoalId(),
          product.latestSnapshotDate(),
          (current, next) -> next.isAfter(current) ? next : current);
    }
    Map<UUID, GoalValue> values = new HashMap<>();
    for (UUID goalId : goalIds) {
      BigDecimal sum = sums.getOrDefault(goalId, BigDecimal.ZERO);
      values.put(
          goalId, new GoalValue(sum.divide(HUNDRED, 2, RoundingMode.HALF_UP), dates.get(goalId)));
    }
    return values;
  }

  GoalValue valueOf(UUID goalId, LocalDate asOf) {
    return valuesByGoal(java.util.List.of(goalId), asOf).get(goalId);
  }
}
