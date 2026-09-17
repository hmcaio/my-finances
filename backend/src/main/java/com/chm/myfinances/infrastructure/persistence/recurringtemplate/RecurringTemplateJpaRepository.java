package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RecurringTemplateJpaEntity}. Not exposed outside this package.
 */
interface RecurringTemplateJpaRepository extends JpaRepository<RecurringTemplateJpaEntity, UUID> {

  List<RecurringTemplateJpaEntity> findByActiveTrue();

  List<RecurringTemplateJpaEntity> findByAccountId(UUID accountId);
}
