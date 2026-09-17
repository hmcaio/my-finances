package com.chm.myfinances.infrastructure.persistence.budget;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link BudgetJpaEntity}. Not exposed outside this package. */
interface BudgetJpaRepository extends JpaRepository<BudgetJpaEntity, UUID> {

  boolean existsByCategoryId(UUID categoryId);
}
