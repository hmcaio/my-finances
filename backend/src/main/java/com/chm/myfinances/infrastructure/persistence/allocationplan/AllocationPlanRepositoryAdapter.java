package com.chm.myfinances.infrastructure.persistence.allocationplan;

import com.chm.myfinances.domain.allocationplan.AllocationPlan;
import com.chm.myfinances.domain.allocationplan.AllocationPlanRepository;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link AllocationPlanRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). There is at most one row ever (an application-layer invariant, not a
 * DB constraint - the marker row carries no distinguishing column to constrain on).
 */
@Component
public class AllocationPlanRepositoryAdapter implements AllocationPlanRepository {

  private final AllocationPlanJpaRepository jpaRepository;

  public AllocationPlanRepositoryAdapter(AllocationPlanJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public AllocationPlan save(AllocationPlan plan) {
    AllocationPlanJpaEntity entity =
        jpaRepository
            .findById(plan.getId())
            .orElseGet(() -> new AllocationPlanJpaEntity(plan.getId()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<AllocationPlan> findFirst() {
    return jpaRepository.findAll(PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(AllocationPlanRepositoryAdapter::toDomain);
  }

  private static AllocationPlan toDomain(AllocationPlanJpaEntity entity) {
    return AllocationPlan.reconstitute(entity.getId());
  }
}
