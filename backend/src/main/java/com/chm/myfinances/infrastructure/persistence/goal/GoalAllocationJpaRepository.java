package com.chm.myfinances.infrastructure.persistence.goal;

import com.chm.myfinances.domain.goal.GoalStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link GoalAllocationJpaEntity}. Not exposed outside this package.
 *
 * <p>{@link #deleteByGoalId} is a bulk {@code @Modifying} query, not a derived {@code deleteBy...}
 * method, for the reason documented on {@code AllocationPlanEntryJpaRepository}: Hibernate flushes
 * pending inserts before pending deletes, so re-inserting a product the goal already allocates
 * would hit the still-present row's {@code UNIQUE (goal_id, product_id)}. It flushes first and
 * clears the persistence context after, so no stale row of the old set is read back.
 */
interface GoalAllocationJpaRepository extends JpaRepository<GoalAllocationJpaEntity, UUID> {

  List<GoalAllocationJpaEntity> findByGoalId(UUID goalId);

  List<GoalAllocationJpaEntity> findByGoalIdIn(Collection<UUID> goalIds);

  List<GoalAllocationJpaEntity> findByProductId(UUID productId);

  boolean existsByProductId(UUID productId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("delete from GoalAllocationJpaEntity a where a.goalId = :goalId")
  void deleteByGoalId(@Param("goalId") UUID goalId);

  /**
   * Per product, the percentage other goals already hold, counting only goals whose status is not
   * {@code archived}. Restates the domain's "archived goals free their percentages" rule in JPQL.
   */
  @Query(
      """
      select a.productId, sum(a.percentage)
      from GoalAllocationJpaEntity a, GoalJpaEntity g
      where g.id = a.goalId
        and g.status <> :archived
        and a.goalId <> :goalId
        and a.productId in :productIds
      group by a.productId
      """)
  List<Object[]> sumPercentageByProductExcludingGoal(
      @Param("productIds") Collection<UUID> productIds,
      @Param("goalId") UUID goalId,
      @Param("archived") GoalStatus archived);
}
