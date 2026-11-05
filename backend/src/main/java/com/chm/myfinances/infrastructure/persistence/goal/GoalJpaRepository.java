package com.chm.myfinances.infrastructure.persistence.goal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link GoalJpaEntity}. Not exposed outside this package. */
interface GoalJpaRepository extends JpaRepository<GoalJpaEntity, UUID> {}
