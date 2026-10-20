package com.chm.myfinances.infrastructure.persistence.investmentsegment;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link InvestmentSegmentRepository} port on top of Spring
 * Data/Hibernate (ADR 0004). Translates between the framework-free {@link InvestmentSegment}
 * aggregate and {@link InvestmentSegmentJpaEntity}.
 */
@Component
public class InvestmentSegmentRepositoryAdapter implements InvestmentSegmentRepository {

  private final InvestmentSegmentJpaRepository jpaRepository;

  public InvestmentSegmentRepositoryAdapter(InvestmentSegmentJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public InvestmentSegment save(InvestmentSegment segment) {
    InvestmentSegmentJpaEntity entity =
        jpaRepository
            .findById(segment.getId())
            .map(
                existing -> {
                  existing.setName(segment.getName());
                  return existing;
                })
            .orElseGet(() -> new InvestmentSegmentJpaEntity(segment.getId(), segment.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<InvestmentSegment> findById(UUID id) {
    return jpaRepository.findById(id).map(InvestmentSegmentRepositoryAdapter::toDomain);
  }

  @Override
  public List<InvestmentSegment> findAll() {
    return jpaRepository.findAll().stream()
        .map(InvestmentSegmentRepositoryAdapter::toDomain)
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

  private static InvestmentSegment toDomain(InvestmentSegmentJpaEntity entity) {
    return InvestmentSegment.reconstitute(entity.getId(), entity.getName());
  }
}
