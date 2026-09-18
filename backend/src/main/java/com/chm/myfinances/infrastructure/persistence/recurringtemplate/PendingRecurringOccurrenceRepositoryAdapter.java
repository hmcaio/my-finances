package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link PendingRecurringOccurrenceRepository} port on top of
 * Spring Data/Hibernate (ADR 0004). Translates between the framework-free {@link
 * PendingRecurringOccurrence} value and {@link PendingRecurringOccurrenceJpaEntity}.
 */
@Component
public class PendingRecurringOccurrenceRepositoryAdapter
    implements PendingRecurringOccurrenceRepository {

  private final PendingRecurringOccurrenceJpaRepository jpaRepository;

  public PendingRecurringOccurrenceRepositoryAdapter(
      PendingRecurringOccurrenceJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public PendingRecurringOccurrence save(PendingRecurringOccurrence occurrence) {
    PendingRecurringOccurrenceJpaEntity entity =
        jpaRepository
            .findById(occurrence.getId())
            .orElseGet(
                () ->
                    new PendingRecurringOccurrenceJpaEntity(
                        occurrence.getId(),
                        occurrence.getTemplateId(),
                        occurrence.getTemplateVersionId(),
                        occurrence.getDueDate()));
    entity.setTemplateId(occurrence.getTemplateId());
    entity.setTemplateVersionId(occurrence.getTemplateVersionId());
    entity.setDueDate(occurrence.getDueDate());
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<PendingRecurringOccurrence> findById(UUID id) {
    return jpaRepository.findById(id).map(PendingRecurringOccurrenceRepositoryAdapter::toDomain);
  }

  @Override
  public List<PendingRecurringOccurrence> findAll() {
    return jpaRepository.findAll().stream()
        .map(PendingRecurringOccurrenceRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public void deleteByTemplateId(UUID templateId) {
    jpaRepository.deleteByTemplateId(templateId);
  }

  @Override
  public boolean existsByTemplateIdAndDueDate(UUID templateId, LocalDate dueDate) {
    return jpaRepository.existsByTemplateIdAndDueDate(templateId, dueDate);
  }

  @Override
  public List<PendingRecurringOccurrence> findByTemplateId(UUID templateId) {
    return jpaRepository.findByTemplateId(templateId).stream()
        .map(PendingRecurringOccurrenceRepositoryAdapter::toDomain)
        .toList();
  }

  private static PendingRecurringOccurrence toDomain(PendingRecurringOccurrenceJpaEntity entity) {
    return PendingRecurringOccurrence.reconstitute(
        entity.getId(), entity.getTemplateId(), entity.getTemplateVersionId(), entity.getDueDate());
  }
}
