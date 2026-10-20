package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentProductRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentProduct}
 * aggregate and {@link InvestmentProductJpaEntity}.
 */
@Component
public class InvestmentProductRepositoryAdapter implements InvestmentProductRepository {

  private final InvestmentProductJpaRepository jpaRepository;

  public InvestmentProductRepositoryAdapter(InvestmentProductJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentProduct save(InvestmentProduct product) {
    InvestmentProductJpaEntity entity =
        jpaRepository
            .findById(product.getId())
            .map(
                existing -> {
                  existing.setInvestmentCategoryId(product.getInvestmentCategoryId());
                  existing.setInvestmentSubcategoryId(product.getInvestmentSubcategoryId());
                  existing.setName(product.getName());
                  existing.setAdditionalNotes(product.getAdditionalNotes());
                  existing.setTicker(product.getTicker());
                  existing.setSegmentId(product.getSegmentId());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentProductJpaEntity(
                        product.getId(),
                        product.getInvestmentCategoryId(),
                        product.getInvestmentSubcategoryId(),
                        product.getName(),
                        product.getAdditionalNotes(),
                        product.getTicker(),
                        product.getSegmentId()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentProduct> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentProductRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentProduct> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentProductRepositoryAdapter::toDomain)
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

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return jpaRepository.existsByInvestmentCategoryId(investmentCategoryId);
  }

  @Override
  public boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId) {
    return jpaRepository.existsByInvestmentSubcategoryId(investmentSubcategoryId);
  }

  @Override
  public boolean existsBySegmentId(UUID segmentId) {
    return jpaRepository.existsBySegmentId(segmentId);
  }

  private static InvestmentProduct toDomain(InvestmentProductJpaEntity entity) {
    return InvestmentProduct.reconstitute(
        entity.getId(),
        entity.getInvestmentCategoryId(),
        entity.getInvestmentSubcategoryId(),
        entity.getName(),
        entity.getAdditionalNotes(),
        entity.getTicker(),
        entity.getSegmentId());
  }
}
