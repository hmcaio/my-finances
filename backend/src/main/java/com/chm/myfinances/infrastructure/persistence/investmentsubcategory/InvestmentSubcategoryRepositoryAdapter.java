package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentSubcategoryRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentSubcategory}
 * aggregate and {@link InvestmentSubcategoryJpaEntity}. Only the name is ever updated on an
 * existing row - the parent is immutable.
 */
@Component
public class InvestmentSubcategoryRepositoryAdapter implements InvestmentSubcategoryRepository {

  private final InvestmentSubcategoryJpaRepository jpaRepository;

  public InvestmentSubcategoryRepositoryAdapter(InvestmentSubcategoryJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentSubcategory save(InvestmentSubcategory subcategory) {
    InvestmentSubcategoryJpaEntity entity =
        jpaRepository
            .findById(subcategory.getId())
            .map(
                existing -> {
                  existing.setName(subcategory.getName());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentSubcategoryJpaEntity(
                        subcategory.getId(),
                        subcategory.getInvestmentCategoryId(),
                        subcategory.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentSubcategory> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentSubcategoryRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentSubcategory> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentSubcategoryRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return jpaRepository.existsByInvestmentCategoryId(investmentCategoryId);
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndName(UUID investmentCategoryId, String name) {
    return jpaRepository.existsByInvestmentCategoryIdAndName(investmentCategoryId, name);
  }

  @Override
  public boolean existsByInvestmentCategoryIdAndNameAndIdNot(
      UUID investmentCategoryId, String name, UUID excludedId) {
    return jpaRepository.existsByInvestmentCategoryIdAndNameAndIdNot(
        investmentCategoryId, name, excludedId);
  }

  private static InvestmentSubcategory toDomain(InvestmentSubcategoryJpaEntity entity) {
    return InvestmentSubcategory.reconstitute(
        entity.getId(), entity.getInvestmentCategoryId(), entity.getName());
  }
}
