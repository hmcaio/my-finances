package com.chm.myfinances.domain.investmentsegment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentSegment} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentsegment}.
 */
public interface InvestmentSegmentRepository {

  InvestmentSegment save(InvestmentSegment segment);

  Optional<InvestmentSegment> findById(UUID id);

  List<InvestmentSegment> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether a segment already has this exact name (create guard). */
  boolean existsByName(String name);

  /** Whether a segment other than {@code excludedId} has this exact name (rename guard). */
  boolean existsByNameAndIdNot(String name, UUID excludedId);
}
