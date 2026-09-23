package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The {@code needsSnapshot} flag (F009 spec): a trade changes cash at once but a product's value
 * only with its next snapshot, so as of a date a product is stale when it has a trade (a transfer
 * tagged with it) dated on or before that date and after its latest snapshot on or before that
 * date, or a trade and no snapshot at all. A snapshot on the trade's own date is fresh. Computed on
 * read, never stored - an application-layer query object like {@code AccountBalanceQuery}.
 */
@Service
public class InvestmentSnapshotFreshnessQuery {

  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final TransferRepository transferRepository;

  public InvestmentSnapshotFreshnessQuery(
      LatestInvestmentSnapshotQuery latestSnapshotQuery, TransferRepository transferRepository) {
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.transferRepository = transferRepository;
  }

  /** Ids of every product that {@code needsSnapshot} as of {@code asOfDate}. */
  public Set<UUID> staleProductIds(LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = latestSnapshotQuery.latestByProduct(asOfDate);
    Set<UUID> stale = new HashSet<>();
    for (Transfer trade : transferRepository.findAllInvestmentTrades()) {
      if (trade.getDate().isAfter(asOfDate)) {
        continue;
      }
      InvestmentSnapshot snapshot = latest.get(trade.getInvestmentProductId());
      if (snapshot == null || trade.getDate().isAfter(snapshot.getDate())) {
        stale.add(trade.getInvestmentProductId());
      }
    }
    return stale;
  }

  /** Whether one product {@code needsSnapshot} as of {@code asOfDate}. */
  public boolean needsSnapshot(UUID productId, LocalDate asOfDate) {
    return staleProductIds(asOfDate).contains(productId);
  }
}
