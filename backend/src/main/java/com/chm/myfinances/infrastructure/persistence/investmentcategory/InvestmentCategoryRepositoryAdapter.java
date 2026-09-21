package com.chm.myfinances.infrastructure.persistence.investmentcategory;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentCategoryRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentCategory}
 * aggregate and {@link InvestmentCategoryJpaEntity}.
 */
@Component
public class InvestmentCategoryRepositoryAdapter implements InvestmentCategoryRepository {

  private final InvestmentCategoryJpaRepository jpaRepository;

  public InvestmentCategoryRepositoryAdapter(InvestmentCategoryJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentCategory save(InvestmentCategory category) {
    InvestmentCategoryJpaEntity entity =
        jpaRepository
            .findById(category.getId())
            .map(
                existing -> {
                  existing.setName(category.getName());
                  return existing;
                })
            .orElseGet(() -> new InvestmentCategoryJpaEntity(category.getId(), category.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentCategory> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentCategoryRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentCategory> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentCategoryRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public boolean existsByName(String name) {
    return jpaRepository.existsByName(name);
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return jpaRepository.existsByNameAndIdNot(name, excludedId);
  }

  private static InvestmentCategory toDomain(InvestmentCategoryJpaEntity entity) {
    return InvestmentCategory.reconstitute(entity.getId(), entity.getName());
  }
}
