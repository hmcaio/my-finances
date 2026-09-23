package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * "Latest snapshot per product as of a date" (F009 spec): the value of a product is its most recent
 * snapshot dated on or before the date, and a product with none contributes nothing. An
 * application-layer query object (computed, not stored) reused by the {@code INVESTMENT} branch of
 * {@code AccountBalanceQuery}, the allocation view, the close guard and, later, F010's net worth.
 * Products need no closed-date filter: the close guard means a closed product's latest snapshot is
 * {@code 0}.
 */
@Service
public class LatestInvestmentSnapshotQuery {

  private final InvestmentSnapshotRepository snapshotRepository;

  public LatestInvestmentSnapshotQuery(InvestmentSnapshotRepository snapshotRepository) {
    this.snapshotRepository = snapshotRepository;
  }

  /** The product's latest snapshot on or before {@code asOfDate}, if any. */
  public Optional<InvestmentSnapshot> latestOf(UUID productId, LocalDate asOfDate) {
    return snapshotRepository.findByProductId(productId).stream()
        .filter(snapshot -> !snapshot.getDate().isAfter(asOfDate))
        .findFirst();
  }

  /** The product's most recent snapshot regardless of date (the close guard's question). */
  public Optional<InvestmentSnapshot> latestOf(UUID productId) {
    return latestOf(productId, LocalDate.MAX);
  }

  /**
   * The latest snapshot on or before {@code asOfDate} of every product that has one, keyed by
   * product id. Products with no snapshot by then are absent.
   */
  public Map<UUID, InvestmentSnapshot> latestByProduct(LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = new HashMap<>();
    for (InvestmentSnapshot snapshot : snapshotRepository.findAllOnOrBefore(asOfDate)) {
      latest.merge(
          snapshot.getProductId(),
          snapshot,
          (current, candidate) ->
              candidate.getDate().isAfter(current.getDate()) ? candidate : current);
    }
    return latest;
  }
}
