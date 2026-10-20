package com.chm.myfinances.infrastructure.persistence.allocationplan;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link AllocationPlanJpaEntity}. Not exposed outside this package. */
interface AllocationPlanJpaRepository extends JpaRepository<AllocationPlanJpaEntity, UUID> {}
