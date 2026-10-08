package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The {@code needsSnapshot} flag (F009 spec, keyed by holding for F022/ADR 0020; reworked onto
 * trade-confirmation lines by F027/ADR 0024): a trade changes cash at once but a holding's value
 * only with its next snapshot, so as of a date a holding is stale when it has a line (a {@code
 * TransferTradeLine} tagged with its product *and* its account) dated on or before that date and
 * after its latest snapshot on or before that date, or a line and no snapshot at all. A snapshot on
 * the line's own date is fresh. Computed on read, never stored - an application-layer query object
 * like {@code AccountBalanceQuery}.
 *
 * <p>A line only carries {@code productId}, not a holding id (F022 spec: the holding is looked up
 * at read time, not stored directly), so each line is resolved to its holding by matching the
 * product against whichever of its parent transfer's two endpoints has a holding of it.
 */
@Service
public class InvestmentSnapshotFreshnessQuery {

  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final TransferTradeLineRepository tradeLineRepository;
  private final InvestmentHoldingRepository holdingRepository;

  public InvestmentSnapshotFreshnessQuery(
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      TransferTradeLineRepository tradeLineRepository,
      InvestmentHoldingRepository holdingRepository) {
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.tradeLineRepository = tradeLineRepository;
    this.holdingRepository = holdingRepository;
  }

  /** Ids of every holding that {@code needsSnapshot} as of {@code asOfDate}. */
  public Set<UUID> staleHoldingIds(LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = latestSnapshotQuery.latestByHolding(asOfDate);
    Set<UUID> stale = new HashSet<>();
    for (TransferTradeLine line : tradeLineRepository.findAll()) {
      if (line.date().isAfter(asOfDate)) {
        continue;
      }
      UUID holdingId = resolveHoldingId(line);
      if (holdingId == null) {
        continue;
      }
      InvestmentSnapshot snapshot = latest.get(holdingId);
      if (snapshot == null || line.date().isAfter(snapshot.getDate())) {
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
   * The holding a line belongs to: whichever of its parent transfer's two accounts has an explicit
   * holding of the line's product. {@code null} for an orphaned line (shouldn't happen once
   * holdings are required, but defensive against stale data).
   */
  private UUID resolveHoldingId(TransferTradeLine line) {
    UUID productId = line.productId();
    return holdingRepository
        .findByProductIdAndAccountId(productId, line.fromAccountId())
        .or(() -> holdingRepository.findByProductIdAndAccountId(productId, line.toAccountId()))
        .map(InvestmentHolding::getId)
        .orElse(null);
  }
}
