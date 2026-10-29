package com.chm.myfinances.domain.investmentopeningposition;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentOpeningPosition} (ADR 0004). Implemented by an adapter in
 * {@code infrastructure/persistence/investmentopeningposition}. At most one position exists per
 * holding.
 */
public interface InvestmentOpeningPositionRepository {

  InvestmentOpeningPosition save(InvestmentOpeningPosition position);

  /** The holding's position, if it has one. */
  Optional<InvestmentOpeningPosition> findByHoldingId(UUID holdingId);

  /** The positions of these holdings (holdings without one contribute nothing), one query. */
  List<InvestmentOpeningPosition> findByHoldingIds(Collection<UUID> holdingIds);

  /** Every position of every holding, in no particular order. */
  List<InvestmentOpeningPosition> findAll();

  void deleteById(UUID id);

  /** Whether the holding has a position - part of the holding delete-safety history check. */
  boolean existsByHoldingId(UUID holdingId);
}
