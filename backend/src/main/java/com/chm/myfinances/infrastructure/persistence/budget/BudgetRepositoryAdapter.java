package com.chm.myfinances.infrastructure.persistence.budget;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link BudgetRepository} port on top of Spring Data/Hibernate
 * (ADR 0004). Translates between the framework-free {@link Budget} aggregate and {@link
 * BudgetJpaEntity} - same shape as {@code TransferRepositoryAdapter}.
 */
@Component
public class BudgetRepositoryAdapter implements BudgetRepository {

  private final BudgetJpaRepository jpaRepository;

  public BudgetRepositoryAdapter(BudgetJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Budget save(Budget budget) {
    BudgetJpaEntity entity =
        jpaRepository
            .findById(budget.getId())
            .orElseGet(() -> new BudgetJpaEntity(budget.getId(), budget.getCategoryId()));
    entity.setCategoryId(budget.getCategoryId());
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Budget> findById(UUID id) {
    return jpaRepository.findById(id).map(BudgetRepositoryAdapter::toDomain);
  }

  @Override
  public List<Budget> findAll() {
    return jpaRepository.findAll().stream().map(BudgetRepositoryAdapter::toDomain).toList();
  }

  @Override
  public boolean existsByCategoryId(UUID categoryId) {
    return jpaRepository.existsByCategoryId(categoryId);
  }

  private static Budget toDomain(BudgetJpaEntity entity) {
    return Budget.reconstitute(entity.getId(), entity.getCategoryId());
  }
}
