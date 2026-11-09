package com.chm.myfinances.domain.goal;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository port for {@link GoalAllocation} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/goal}. Every multi-id read is one query for the whole input, never one
 * per element.
 */
public interface GoalAllocationRepository {

  /** The goal's allocations, in no particular order. */
  List<GoalAllocation> findByGoalId(UUID goalId);

  /** The allocations of every goal with these ids (goals without any contribute nothing). */
  List<GoalAllocation> findByGoalIds(Collection<UUID> goalIds);

  /** Every allocation of the product, across all goals, archived ones included. */
  List<GoalAllocation> findByProductId(UUID productId);

  /** Every allocation of every goal, in no particular order. */
  List<GoalAllocation> findAll();

  /**
   * Replaces the goal's whole allocation set with {@code allocations} (delete-then-insert; an empty
   * list clears it).
   */
  void replaceForGoal(UUID goalId, List<GoalAllocation> allocations);

  /**
   * For each of {@code productIds}, the sum of the percentages allocated to it by every goal other
   * than {@code goalId} whose status is not {@code ARCHIVED}. A product nobody else allocates is
   * absent from the map (read it as zero). The "non-archived" filter restates a domain rule in SQL:
   * change it together with {@code GoalStatus} semantics.
   */
  Map<UUID, BigDecimal> sumPercentageByProductExcludingGoal(
      Collection<UUID> productIds, UUID goalId);

  /** Whether any goal, archived or not, allocates this product - backs the product delete guard. */
  boolean existsByProductId(UUID productId);
}
