package com.chm.myfinances.infrastructure.persistence.investmentholding;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentHoldingRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentHolding}
 * aggregate and {@link InvestmentHoldingJpaEntity}.
 */
@Component
public class InvestmentHoldingRepositoryAdapter implements InvestmentHoldingRepository {

  private final InvestmentHoldingJpaRepository jpaRepository;

  public InvestmentHoldingRepositoryAdapter(InvestmentHoldingJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentHolding save(InvestmentHolding holding) {
    InvestmentHoldingJpaEntity entity =
        jpaRepository
            .findById(holding.getId())
            .map(
                existing -> {
                  existing.setClosedDate(holding.getClosedDate());
                  existing.setAdditionalNotes(holding.getAdditionalNotes());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentHoldingJpaEntity(
                        holding.getId(),
                        holding.getProductId(),
                        holding.getAccountId(),
                        holding.getClosedDate(),
                        holding.getAdditionalNotes()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentHolding> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentHoldingRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentHolding> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentHoldingRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<InvestmentHolding> findByProductId(UUID productId) {
    return jpaRepository.findByProductId(productId).stream()
        .map(InvestmentHoldingRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<InvestmentHolding> findByAccountId(UUID accountId) {
    return jpaRepository.findByAccountId(accountId).stream()
        .map(InvestmentHoldingRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public Optional<InvestmentHolding> findByProductIdAndAccountId(UUID productId, UUID accountId) {
    return jpaRepository
        .findByProductIdAndAccountId(productId, accountId)
        .map(InvestmentHoldingRepositoryAdapter::toDomain);
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
  public boolean existsByProductIdAndAccountId(UUID productId, UUID accountId) {
    return jpaRepository.existsByProductIdAndAccountId(productId, accountId);
  }

  @Override
  public boolean existsByProductId(UUID productId) {
    return jpaRepository.existsByProductId(productId);
  }

  @Override
  public boolean existsByAccountId(UUID accountId) {
    return jpaRepository.existsByAccountId(accountId);
  }

  @Override
  public boolean existsOpenByAccountId(UUID accountId) {
    return jpaRepository.existsByAccountIdAndClosedDateIsNull(accountId);
  }

  private static InvestmentHolding toDomain(InvestmentHoldingJpaEntity entity) {
    return InvestmentHolding.reconstitute(
        entity.getId(),
        entity.getProductId(),
        entity.getAccountId(),
        entity.getClosedDate(),
        entity.getAdditionalNotes());
  }
}
