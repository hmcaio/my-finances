package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory test double for {@link GoalAllocationRepository} (F039). It needs the {@link
 * FakeGoalRepository} the allocations belong to, because the per-product sum counts only
 * non-archived goals. Does not enforce the {@code UNIQUE (goal_id, product_id)} constraint or the
 * foreign keys - the real adapter test covers those.
 */
public final class FakeGoalAllocationRepository implements GoalAllocationRepository {

  private final FakeGoalRepository goalRepository;
  private final List<GoalAllocation> store = new ArrayList<>();

  public FakeGoalAllocationRepository(FakeGoalRepository goalRepository) {
    this.goalRepository = goalRepository;
  }

  @Override
  public List<GoalAllocation> findByGoalId(UUID goalId) {
    return store.stream().filter(a -> a.getGoalId().equals(goalId)).toList();
  }

  @Override
  public List<GoalAllocation> findByGoalIds(Collection<UUID> goalIds) {
    return store.stream().filter(a -> goalIds.contains(a.getGoalId())).toList();
  }

  @Override
  public List<GoalAllocation> findByProductId(UUID productId) {
    return store.stream().filter(a -> a.getProductId().equals(productId)).toList();
  }

  @Override
  public List<GoalAllocation> findAll() {
    return List.copyOf(store);
  }

  @Override
  public void replaceForGoal(UUID goalId, List<GoalAllocation> allocations) {
    store.removeIf(a -> a.getGoalId().equals(goalId));
    store.addAll(allocations);
  }

  @Override
  public Map<UUID, BigDecimal> sumPercentageByProductExcludingGoal(
      Collection<UUID> productIds, UUID goalId) {
    Map<UUID, BigDecimal> sums = new HashMap<>();
    for (GoalAllocation allocation : store) {
      if (allocation.getGoalId().equals(goalId)
          || !productIds.contains(allocation.getProductId())) {
        continue;
      }
      boolean archived =
          goalRepository
              .findById(allocation.getGoalId())
              .map(Goal::getStatus)
              .filter(status -> status == GoalStatus.ARCHIVED)
              .isPresent();
      if (!archived) {
        sums.merge(allocation.getProductId(), allocation.getPercentage(), BigDecimal::add);
      }
    }
    return sums;
  }

  @Override
  public boolean existsByProductId(UUID productId) {
    return store.stream().anyMatch(a -> a.getProductId().equals(productId));
  }
}
