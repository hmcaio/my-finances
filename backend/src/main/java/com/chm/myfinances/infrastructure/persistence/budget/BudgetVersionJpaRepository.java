package com.chm.myfinances.infrastructure.persistence.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link BudgetVersionJpaEntity}. Not exposed outside this package. */
interface BudgetVersionJpaRepository extends JpaRepository<BudgetVersionJpaEntity, UUID> {

  List<BudgetVersionJpaEntity> findByBudgetId(UUID budgetId);

  Optional<BudgetVersionJpaEntity> findByBudgetIdAndEffectiveFrom(
      UUID budgetId, LocalDate effectiveFrom);
}
