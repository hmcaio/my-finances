package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link RecurringTemplateVersionRepository} port on top of
 * Spring Data/Hibernate (ADR 0004). Translates between the framework-free {@link
 * RecurringTemplateVersion} aggregate and {@link RecurringTemplateVersionJpaEntity}, same shape as
 * F006's {@code BudgetVersionRepositoryAdapter}.
 */
@Component
public class RecurringTemplateVersionRepositoryAdapter
    implements RecurringTemplateVersionRepository {

  private final RecurringTemplateVersionJpaRepository jpaRepository;

  public RecurringTemplateVersionRepositoryAdapter(
      RecurringTemplateVersionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public RecurringTemplateVersion save(RecurringTemplateVersion version) {
    RecurringTemplateVersionJpaEntity entity =
        jpaRepository
            .findById(version.getId())
            .orElseGet(
                () ->
                    new RecurringTemplateVersionJpaEntity(
                        version.getId(),
                        version.getTemplateId(),
                        version.getAmount(),
                        version.getDayOfMonth(),
                        toFirstOfMonth(version.getEffectiveFrom())));
    entity.setTemplateId(version.getTemplateId());
    entity.setAmount(version.getAmount());
    entity.setDayOfMonth(version.getDayOfMonth());
    entity.setEffectiveFrom(toFirstOfMonth(version.getEffectiveFrom()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<RecurringTemplateVersion> findById(UUID id) {
    return jpaRepository.findById(id).map(RecurringTemplateVersionRepositoryAdapter::toDomain);
  }

  @Override
  public List<RecurringTemplateVersion> findByTemplateId(UUID templateId) {
    return jpaRepository.findByTemplateId(templateId).stream()
        .map(RecurringTemplateVersionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<RecurringTemplateVersion> findByTemplateIdAndEffectiveFrom(
      UUID templateId, YearMonth effectiveFrom) {
    return jpaRepository
        .findByTemplateIdAndEffectiveFrom(templateId, toFirstOfMonth(effectiveFrom))
        .map(RecurringTemplateVersionRepositoryAdapter::toDomain);
  }

  private static java.time.LocalDate toFirstOfMonth(YearMonth yearMonth) {
    return yearMonth.atDay(1);
  }

  private static RecurringTemplateVersion toDomain(RecurringTemplateVersionJpaEntity entity) {
    return RecurringTemplateVersion.reconstitute(
        entity.getId(),
        entity.getTemplateId(),
        entity.getAmount(),
        entity.getDayOfMonth(),
        YearMonth.from(entity.getEffectiveFrom()));
  }
}
