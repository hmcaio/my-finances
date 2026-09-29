package com.chm.myfinances.domain.investmentsnapshot;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link InvestmentSnapshot} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/investmentsnapshot}.
 */
public interface InvestmentSnapshotRepository {

  InvestmentSnapshot save(InvestmentSnapshot snapshot);

  Optional<InvestmentSnapshot> findById(UUID id);

  void deleteById(UUID id);

  /** The holding's snapshot on exactly this date, for the same-day upsert. */
  Optional<InvestmentSnapshot> findByHoldingIdAndDate(UUID holdingId, LocalDate date);

  /** Every snapshot of the holding, most recent date first. */
  List<InvestmentSnapshot> findByHoldingId(UUID holdingId);

  /**
   * Every snapshot of every holding dated on or before {@code asOfDate}, for {@code
   * LatestInvestmentSnapshotQuery} to reduce to the latest per holding.
   */
  List<InvestmentSnapshot> findAllOnOrBefore(LocalDate asOfDate);

  /** Every snapshot of every holding, in no particular order. */
  List<InvestmentSnapshot> findAll();

  /** Whether the holding has any snapshot - half of the delete-safety history check. */
  boolean existsByHoldingId(UUID holdingId);
}
