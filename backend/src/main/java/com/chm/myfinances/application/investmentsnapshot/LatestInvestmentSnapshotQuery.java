package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * "Latest snapshot per holding as of a date" (F009 spec, rekeyed by holding for F022/ADR 0020): the
 * value of a holding is its most recent snapshot dated on or before the date, and a holding with
 * none contributes nothing. An application-layer query object (computed, not stored) reused by the
 * {@code INVESTMENT} branch of {@code AccountBalanceQuery}, the allocation view, the close guard
 * and F010's net worth. Holdings need no closed-date filter: the close guard means a closed
 * holding's latest snapshot is {@code 0}.
 *
 * <p>{@link #totalValueByProduct} rolls holdings back up to the product level (a product's value is
 * the sum of its holdings' latest snapshots) - used by the category/sub-category allocation, since
 * a product can be held at more than one account.
 */
@Service
public class LatestInvestmentSnapshotQuery {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final InvestmentHoldingRepository holdingRepository;

  public LatestInvestmentSnapshotQuery(
      InvestmentSnapshotRepository snapshotRepository,
      InvestmentHoldingRepository holdingRepository) {
    this.snapshotRepository = snapshotRepository;
    this.holdingRepository = holdingRepository;
  }

  /** The holding's latest snapshot on or before {@code asOfDate}, if any. */
  public Optional<InvestmentSnapshot> latestOf(UUID holdingId, LocalDate asOfDate) {
    return snapshotRepository.findByHoldingId(holdingId).stream()
        .filter(snapshot -> !snapshot.getDate().isAfter(asOfDate))
        .findFirst();
  }

  /** The holding's most recent snapshot regardless of date (the close guard's question). */
  public Optional<InvestmentSnapshot> latestOf(UUID holdingId) {
    return latestOf(holdingId, LocalDate.MAX);
  }

  /**
   * The latest snapshot on or before {@code asOfDate} of every holding that has one, keyed by
   * holding id. Holdings with no snapshot by then are absent.
   */
  public Map<UUID, InvestmentSnapshot> latestByHolding(LocalDate asOfDate) {
    return reduceToLatest(snapshotRepository.findAllOnOrBefore(asOfDate));
  }

  /** The most recent snapshot of every holding that has one, regardless of date. */
  public Map<UUID, InvestmentSnapshot> latestByHolding() {
    return reduceToLatest(snapshotRepository.findAll());
  }

  /**
   * The product's total value as of {@code asOfDate}: the sum of the latest snapshots of every
   * holding of that product (F022 - a product can be held at more than one account).
   */
  public BigDecimal totalValueOfProduct(UUID productId, LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = latestByHolding(asOfDate);
    BigDecimal total = BigDecimal.ZERO;
    for (InvestmentHolding holding : holdingRepository.findByProductId(productId)) {
      InvestmentSnapshot snapshot = latest.get(holding.getId());
      if (snapshot != null) {
        total = total.add(snapshot.getBalance());
      }
    }
    return total;
  }

  private static Map<UUID, InvestmentSnapshot> reduceToLatest(List<InvestmentSnapshot> snapshots) {
    Map<UUID, InvestmentSnapshot> latest = new HashMap<>();
    for (InvestmentSnapshot snapshot : snapshots) {
      latest.merge(
          snapshot.getHoldingId(),
          snapshot,
          (current, candidate) ->
              candidate.getDate().isAfter(current.getDate()) ? candidate : current);
    }
    return latest;
  }
}
