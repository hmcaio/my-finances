package com.chm.myfinances.infrastructure.persistence.budget;

import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link BudgetVersionRepository} port on top of Spring Data/
 * Hibernate (ADR 0004). Translates between the framework-free {@link BudgetVersion} aggregate and
 * {@link BudgetVersionJpaEntity} - including the {@code YearMonth} <-> "first day of the month
 * {@code LocalDate}" conversion the F006 spec calls for, kept entirely at this boundary so the
 * domain never deals in persistence representation.
 */
@Component
public class BudgetVersionRepositoryAdapter implements BudgetVersionRepository {

  private final BudgetVersionJpaRepository jpaRepository;

  public BudgetVersionRepositoryAdapter(BudgetVersionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public BudgetVersion save(BudgetVersion version) {
    BudgetVersionJpaEntity entity =
        jpaRepository
            .findById(version.getId())
            .orElseGet(
                () ->
                    new BudgetVersionJpaEntity(
                        version.getId(),
                        version.getBudgetId(),
                        version.getMonthlyCap(),
                        toFirstOfMonth(version.getEffectiveFrom())));
    entity.setBudgetId(version.getBudgetId());
    entity.setMonthlyCap(version.getMonthlyCap());
    entity.setEffectiveFrom(toFirstOfMonth(version.getEffectiveFrom()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public List<BudgetVersion> findByBudgetId(UUID budgetId) {
    return jpaRepository.findByBudgetId(budgetId).stream()
        .map(BudgetVersionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<BudgetVersion> findByBudgetIdAndEffectiveFrom(
      UUID budgetId, YearMonth effectiveFrom) {
    return jpaRepository
        .findByBudgetIdAndEffectiveFrom(budgetId, toFirstOfMonth(effectiveFrom))
        .map(BudgetVersionRepositoryAdapter::toDomain);
  }

  private static LocalDate toFirstOfMonth(YearMonth yearMonth) {
    return yearMonth.atDay(1);
  }

  private static BudgetVersion toDomain(BudgetVersionJpaEntity entity) {
    return BudgetVersion.reconstitute(
        entity.getId(),
        entity.getBudgetId(),
        entity.getMonthlyCap(),
        YearMonth.from(entity.getEffectiveFrom()));
  }
}
