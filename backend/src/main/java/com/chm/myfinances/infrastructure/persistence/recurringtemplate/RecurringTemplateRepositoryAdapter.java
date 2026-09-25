package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link RecurringTemplateRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link RecurringTemplate}
 * aggregate and {@link RecurringTemplateJpaEntity} - including the {@code YearMonth} <-> "first day
 * of the month {@code LocalDate}" conversion for {@code lastGeneratedFor}, same convention as
 * F006's {@code BudgetVersionRepositoryAdapter}.
 */
@Component
public class RecurringTemplateRepositoryAdapter implements RecurringTemplateRepository {

  private final RecurringTemplateJpaRepository jpaRepository;

  public RecurringTemplateRepositoryAdapter(RecurringTemplateJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public RecurringTemplate save(RecurringTemplate template) {
    RecurringTemplateJpaEntity entity =
        jpaRepository.findById(template.getId()).orElseGet(RecurringTemplateJpaEntity::new);
    entity.setId(template.getId());
    entity.setCategoryId(template.getCategoryId());
    entity.setAccountId(template.getAccountId());
    entity.setDescription(template.getDescription());
    entity.setActive(template.isActive());
    entity.setLastGeneratedFor(toFirstOfMonth(template.getLastGeneratedFor()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<RecurringTemplate> findById(UUID id) {
    return jpaRepository.findById(id).map(RecurringTemplateRepositoryAdapter::toDomain);
  }

  @Override
  public List<RecurringTemplate> findAll() {
    return jpaRepository.findAll().stream()
        .map(RecurringTemplateRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<RecurringTemplate> findAllActive() {
    return jpaRepository.findByActiveTrue().stream()
        .map(RecurringTemplateRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<RecurringTemplate> findByAccountId(UUID accountId) {
    return jpaRepository.findByAccountId(accountId).stream()
        .map(RecurringTemplateRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return jpaRepository.existsByCategoryId(categoryId);
  }

  @Override
  public boolean existsByAccountId(UUID accountId) {
    return jpaRepository.existsByAccountId(accountId);
  }

  private static LocalDate toFirstOfMonth(YearMonth yearMonth) {
    return yearMonth == null ? null : yearMonth.atDay(1);
  }

  private static RecurringTemplate toDomain(RecurringTemplateJpaEntity entity) {
    YearMonth lastGeneratedFor =
        entity.getLastGeneratedFor() == null ? null : YearMonth.from(entity.getLastGeneratedFor());
    return RecurringTemplate.reconstitute(
        entity.getId(),
        entity.getCategoryId(),
        entity.getAccountId(),
        entity.getDescription(),
        entity.isActive(),
        lastGeneratedFor);
  }
}
