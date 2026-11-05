package com.chm.myfinances.infrastructure.persistence.goal;

import com.chm.myfinances.domain.goal.Goal;
import com.chm.myfinances.domain.goal.GoalRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link GoalRepository} port on top of Spring Data/Hibernate
 * (ADR 0004). Translates between the framework-free {@link Goal} aggregate and {@link
 * GoalJpaEntity}. Deleting a goal removes its allocations through the {@code ON DELETE CASCADE} of
 * {@code goal_allocations} (V28).
 */
@Component
public class GoalRepositoryAdapter implements GoalRepository {

  private final GoalJpaRepository jpaRepository;

  public GoalRepositoryAdapter(GoalJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Goal save(Goal goal) {
    GoalJpaEntity entity =
        jpaRepository
            .findById(goal.getId())
            .map(
                existing -> {
                  copyEditable(goal, existing);
                  return existing;
                })
            .orElseGet(() -> toNewEntity(goal));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Goal> findById(UUID id) {
    return jpaRepository.findById(id).map(GoalRepositoryAdapter::toDomain);
  }

  @Override
  public List<Goal> findAll() {
    return jpaRepository.findAll().stream().map(GoalRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  private static void copyEditable(Goal goal, GoalJpaEntity entity) {
    entity.setName(goal.getName());
    entity.setNotes(goal.getNotes());
    entity.setTargetAmount(goal.getTargetAmount());
    entity.setTargetDate(goal.getTargetDate());
    entity.setPlannedMonthlyContribution(goal.getPlannedMonthlyContribution());
    entity.setReturnPessimistic(goal.getReturnPessimistic());
    entity.setReturnBase(goal.getReturnBase());
    entity.setReturnOptimistic(goal.getReturnOptimistic());
    entity.setStatus(goal.getStatus());
    entity.setShowOnDashboard(goal.isShowOnDashboard());
  }

  private static GoalJpaEntity toNewEntity(Goal goal) {
    return new GoalJpaEntity(
        goal.getId(),
        goal.getName(),
        goal.getNotes(),
        goal.getTargetAmount(),
        goal.getTargetDate(),
        goal.getPlannedMonthlyContribution(),
        goal.getReturnPessimistic(),
        goal.getReturnBase(),
        goal.getReturnOptimistic(),
        goal.getStatus(),
        goal.isShowOnDashboard());
  }

  private static Goal toDomain(GoalJpaEntity entity) {
    return Goal.reconstitute(
        entity.getId(),
        entity.getName(),
        entity.getNotes(),
        entity.getTargetAmount(),
        entity.getTargetDate(),
        entity.getPlannedMonthlyContribution(),
        entity.getReturnPessimistic(),
        entity.getReturnBase(),
        entity.getReturnOptimistic(),
        entity.getStatus(),
        entity.isShowOnDashboard());
  }
}
