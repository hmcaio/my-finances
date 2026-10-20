package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link RecurringTemplateVersionJpaEntity}. Not exposed outside this
 * package.
 */
interface RecurringTemplateVersionJpaRepository
    extends JpaRepository<RecurringTemplateVersionJpaEntity, UUID> {

  List<RecurringTemplateVersionJpaEntity> findByTemplateId(UUID templateId);

  Optional<RecurringTemplateVersionJpaEntity> findByTemplateIdAndEffectiveFrom(
      UUID templateId, LocalDate effectiveFrom);
}
