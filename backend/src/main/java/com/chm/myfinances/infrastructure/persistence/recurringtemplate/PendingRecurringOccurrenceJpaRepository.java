package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PendingRecurringOccurrenceJpaEntity}. Not exposed outside this
 * package.
 */
interface PendingRecurringOccurrenceJpaRepository
    extends JpaRepository<PendingRecurringOccurrenceJpaEntity, UUID> {

  void deleteByTemplateId(UUID templateId);

  boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate);

  List<PendingRecurringOccurrenceJpaEntity> findByTemplateId(UUID templateId);
}
