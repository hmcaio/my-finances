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
                  existing.setAccountId(product.getAccountId());
                  existing.setInvestmentCategoryId(product.getInvestmentCategoryId());
                  existing.setInvestmentSubcategoryId(product.getInvestmentSubcategoryId());
                  existing.setName(product.getName());
                  existing.setClosedDate(product.getClosedDate());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentProductJpaEntity(
                        product.getId(),
                        product.getAccountId(),
                        product.getInvestmentCategoryId(),
                        product.getInvestmentSubcategoryId(),
                        product.getName(),
                        product.getClosedDate()));
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
  public List<InvestmentProduct> findByAccountId(UUID accountId) {
    return jpaRepository.findByAccountId(accountId).stream()
        .map(InvestmentProductRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsByAccountIdAndName(UUID accountId, String name) {
    return jpaRepository.existsByAccountIdAndName(accountId, name);
  }

  @Override
  public boolean existsByAccountIdAndNameAndIdNot(UUID accountId, String name, UUID excludedId) {
    return jpaRepository.existsByAccountIdAndNameAndIdNot(accountId, name, excludedId);
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return jpaRepository.existsByAccountIdAndClosedDateIsNull(accountId);
  }

  @Override
  public boolean existsByInvestmentCategoryId(UUID investmentCategoryId) {
    return jpaRepository.existsByInvestmentCategoryId(investmentCategoryId);
  }

  @Override
  public boolean existsByInvestmentSubcategoryId(UUID investmentSubcategoryId) {
    return jpaRepository.existsByInvestmentSubcategoryId(investmentSubcategoryId);
  }

  private static InvestmentProduct toDomain(InvestmentProductJpaEntity entity) {
    return InvestmentProduct.reconstitute(
        entity.getId(),
        entity.getAccountId(),
        entity.getInvestmentCategoryId(),
        entity.getInvestmentSubcategoryId(),
        entity.getName(),
        entity.getClosedDate());
  }
}
