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

  /** The product's snapshot on exactly this date, for the same-day upsert. */
  Optional<InvestmentSnapshot> findByProductIdAndDate(UUID productId, LocalDate date);

  /** Every snapshot of the product, most recent date first. */
  List<InvestmentSnapshot> findByProductId(UUID productId);

  /**
   * Every snapshot of every product dated on or before {@code asOfDate}, for {@code
   * LatestInvestmentSnapshotQuery} to reduce to the latest per product.
   */
  List<InvestmentSnapshot> findAllOnOrBefore(LocalDate asOfDate);

  /** Every snapshot of every product, in no particular order. */
  List<InvestmentSnapshot> findAll();

  /** Whether the product has any snapshot - half of the delete-safety history check. */
  boolean existsByProductId(UUID productId);
}
