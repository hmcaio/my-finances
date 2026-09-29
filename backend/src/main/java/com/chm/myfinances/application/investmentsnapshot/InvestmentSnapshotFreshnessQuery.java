package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
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
 * The {@code needsSnapshot} flag (F009 spec, keyed by holding for F022/ADR 0020): a trade changes
 * cash at once but a holding's value only with its next snapshot, so as of a date a holding is
 * stale when it has a trade (a transfer tagged with its product *and* its account) dated on or
 * before that date and after its latest snapshot on or before that date, or a trade and no snapshot
 * at all. A snapshot on the trade's own date is fresh. Computed on read, never stored - an
 * application-layer query object like {@code AccountBalanceQuery}.
 *
 * <p>A trade only carries {@code investmentProductId}, not a holding id (F022 spec: the holding is
 * looked up at read time, not stored on the transfer), so each trade is resolved to its holding by
 * matching the product against whichever of the transfer's two endpoints has a holding of it.
 */
@Service
public class InvestmentSnapshotFreshnessQuery {

  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final TransferRepository transferRepository;
  private final InvestmentHoldingRepository holdingRepository;

  public InvestmentSnapshotFreshnessQuery(
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      TransferRepository transferRepository,
      InvestmentHoldingRepository holdingRepository) {
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.transferRepository = transferRepository;
    this.holdingRepository = holdingRepository;
  }

  /** Ids of every holding that {@code needsSnapshot} as of {@code asOfDate}. */
  public Set<UUID> staleHoldingIds(LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = latestSnapshotQuery.latestByHolding(asOfDate);
    Set<UUID> stale = new HashSet<>();
    for (Transfer trade : transferRepository.findAllInvestmentTrades()) {
      if (trade.getDate().isAfter(asOfDate)) {
        continue;
      }
      UUID holdingId = resolveHoldingId(trade);
      if (holdingId == null) {
        continue;
      }
      InvestmentSnapshot snapshot = latest.get(holdingId);
      if (snapshot == null || trade.getDate().isAfter(snapshot.getDate())) {
        stale.add(holdingId);
      }
    }
    return stale;
  }

  /** Whether one holding {@code needsSnapshot} as of {@code asOfDate}. */
  public boolean needsSnapshot(UUID holdingId, LocalDate asOfDate) {
    return staleHoldingIds(asOfDate).contains(holdingId);
  }

  /**
   * The holding a trade belongs to: whichever of its two accounts has an explicit holding of the
   * trade's product. {@code null} for an orphaned trade (shouldn't happen once holdings are
   * required, but defensive against stale data).
   */
  private UUID resolveHoldingId(Transfer trade) {
    UUID productId = trade.getInvestmentProductId();
    return holdingRepository
        .findByProductIdAndAccountId(productId, trade.getFromAccountId())
        .or(() -> holdingRepository.findByProductIdAndAccountId(productId, trade.getToAccountId()))
        .map(InvestmentHolding::getId)
        .orElse(null);
  }
}
