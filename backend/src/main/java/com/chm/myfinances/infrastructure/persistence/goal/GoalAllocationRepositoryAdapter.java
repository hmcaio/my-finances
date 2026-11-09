package com.chm.myfinances.infrastructure.persistence.goal;

import com.chm.myfinances.domain.goal.GoalAllocation;
import com.chm.myfinances.domain.goal.GoalAllocationRepository;
import com.chm.myfinances.domain.goal.GoalStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter implementing the domain's {@link GoalAllocationRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). {@link #replaceForGoal} replaces the whole set (bulk delete, then
 * insert) in one transaction.
 */
@Component
public class GoalAllocationRepositoryAdapter implements GoalAllocationRepository {

  private final GoalAllocationJpaRepository jpaRepository;

  public GoalAllocationRepositoryAdapter(GoalAllocationJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public List<GoalAllocation> findByGoalId(UUID goalId) {
    return jpaRepository.findByGoalId(goalId).stream()
        .map(GoalAllocationRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<GoalAllocation> findByGoalIds(Collection<UUID> goalIds) {
    if (goalIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findByGoalIdIn(goalIds).stream()
        .map(GoalAllocationRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<GoalAllocation> findByProductId(UUID productId) {
    return jpaRepository.findByProductId(productId).stream()
        .map(GoalAllocationRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<GoalAllocation> findAll() {
    return jpaRepository.findAll().stream().map(GoalAllocationRepositoryAdapter::toDomain).toList();
  }

  @Override
  @Transactional
  public void replaceForGoal(UUID goalId, List<GoalAllocation> allocations) {
    jpaRepository.deleteByGoalId(goalId);
    for (GoalAllocation allocation : allocations) {
      jpaRepository.save(
          new GoalAllocationJpaEntity(
              allocation.getId(),
              allocation.getGoalId(),
              allocation.getProductId(),
              allocation.getPercentage()));
    }
  }

  @Override
  public Map<UUID, BigDecimal> sumPercentageByProductExcludingGoal(
      Collection<UUID> productIds, UUID goalId) {
    if (productIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, BigDecimal> sums = new HashMap<>();
    for (Object[] row :
        jpaRepository.sumPercentageByProductExcludingGoal(
            productIds, goalId, GoalStatus.ARCHIVED)) {
      sums.put((UUID) row[0], (BigDecimal) row[1]);
    }
    return sums;
  }

  @Override
  public boolean existsByProductId(UUID productId) {
    return jpaRepository.existsByProductId(productId);
  }

  private static GoalAllocation toDomain(GoalAllocationJpaEntity entity) {
    return GoalAllocation.reconstitute(
        entity.getId(), entity.getGoalId(), entity.getProductId(), entity.getPercentage());
  }
}
