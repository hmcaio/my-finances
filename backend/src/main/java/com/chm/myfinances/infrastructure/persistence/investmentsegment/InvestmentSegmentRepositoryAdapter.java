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
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  private static InvestmentSegment toDomain(InvestmentSegmentJpaEntity entity) {
    return InvestmentSegment.reconstitute(entity.getId(), entity.getName());
  }
}
