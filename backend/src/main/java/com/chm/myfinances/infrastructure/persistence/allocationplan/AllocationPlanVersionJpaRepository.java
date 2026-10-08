package com.chm.myfinances.infrastructure.persistence.allocationplan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link AllocationPlanVersionJpaEntity}. Not exposed outside this
 * package.
 */
interface AllocationPlanVersionJpaRepository extends JpaRepository<AllocationPlanVersionJpaEntity, UUID> {

  List<AllocationPlanVersionJpaEntity> findByPlanId(UUID planId);

  Optional<AllocationPlanVersionJpaEntity> findByPlanIdAndEffectiveFrom(UUID planId, LocalDate effectiveFrom);
}
