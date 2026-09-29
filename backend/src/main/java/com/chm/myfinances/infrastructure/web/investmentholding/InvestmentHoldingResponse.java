package com.chm.myfinances.infrastructure.web.investmentholding;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentHolding} (F022 spec). {@code hasHistory} (a snapshot or
 * a tagged transfer) tells the client whether the holding can still be hard-deleted or can only be
 * closed. {@code latestSnapshot} (moved here from the old product response) and {@code
 * needsSnapshot} are computed on read, never stored.
 */
public record InvestmentHoldingResponse(
    UUID id,
    UUID productId,
    UUID accountId,
    LocalDate closedDate,
    boolean closed,
    String additionalNotes,
    boolean hasHistory,
    boolean needsSnapshot,
    LatestSnapshotResponse latestSnapshot) {

  /** The date and value of a holding's most recent snapshot. */
  public record LatestSnapshotResponse(LocalDate date, BigDecimal balance) {

    static LatestSnapshotResponse from(InvestmentSnapshot snapshot) {
      return snapshot == null
          ? null
          : new LatestSnapshotResponse(snapshot.getDate(), snapshot.getBalance());
    }
  }

  public static InvestmentHoldingResponse from(
      InvestmentHolding holding,
      boolean hasHistory,
      boolean needsSnapshot,
      InvestmentSnapshot latestSnapshot) {
    return new InvestmentHoldingResponse(
        holding.getId(),
        holding.getProductId(),
        holding.getAccountId(),
        holding.getClosedDate(),
        holding.isClosed(),
        holding.getAdditionalNotes(),
        hasHistory,
        needsSnapshot,
        LatestSnapshotResponse.from(latestSnapshot));
  }
}
