package com.chm.myfinances.infrastructure.persistence.investmentopeningposition;

import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentOpeningPositionRepository} port on top of
 * Spring Data/Hibernate (ADR 0004). Translates between the framework-free {@link
 * InvestmentOpeningPosition} aggregate and {@link InvestmentOpeningPositionJpaEntity}.
 */
@Component
public class InvestmentOpeningPositionRepositoryAdapter
    implements InvestmentOpeningPositionRepository {

  private final InvestmentOpeningPositionJpaRepository jpaRepository;

  public InvestmentOpeningPositionRepositoryAdapter(
      InvestmentOpeningPositionJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentOpeningPosition save(InvestmentOpeningPosition position) {
    InvestmentOpeningPositionJpaEntity entity =
        jpaRepository
            .findById(position.getId())
            .map(
                existing -> {
                  existing.setAsOfDate(position.getAsOfDate());
                  existing.setQuantity(position.getQuantity());
                  existing.setUnitPrice(position.getUnitPrice());
                  return existing;
                })
            .orElseGet(
                () ->
                    new InvestmentOpeningPositionJpaEntity(
                        position.getId(),
                        position.getHoldingId(),
                        position.getAsOfDate(),
                        position.getQuantity(),
                        position.getUnitPrice()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentOpeningPosition> findByHoldingId(UUID holdingId) {
    return jpaRepository
        .findByHoldingId(holdingId)
        .map(InvestmentOpeningPositionRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentOpeningPosition> findByHoldingIds(Collection<UUID> holdingIds) {
    if (holdingIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findByHoldingIdIn(holdingIds).stream()
        .map(InvestmentOpeningPositionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<InvestmentOpeningPosition> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentOpeningPositionRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsByHoldingId(UUID holdingId) {
    return jpaRepository.existsByHoldingId(holdingId);
  }

  private static InvestmentOpeningPosition toDomain(InvestmentOpeningPositionJpaEntity entity) {
    return InvestmentOpeningPosition.reconstitute(
        entity.getId(),
        entity.getHoldingId(),
        entity.getAsOfDate(),
        entity.getQuantity(),
        entity.getUnitPrice());
  }
}
