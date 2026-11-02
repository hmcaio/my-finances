package com.chm.myfinances.domain.investmentsegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentSegment} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentsegment}.
 */
public interface InvestmentSegmentRepository {

  Optional<InvestmentSegment> findById(UUID id);

  List<InvestmentSegment> findAll();

  boolean existsById(UUID id);
}
