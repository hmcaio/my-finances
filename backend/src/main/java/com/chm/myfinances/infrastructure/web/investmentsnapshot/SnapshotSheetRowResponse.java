package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.application.investmentsnapshot.OpenHoldingSnapshotRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of {@code GET /api/investment-snapshot-sheet}: an open holding and its latest snapshot.
 */
public record SnapshotSheetRowResponse(
    UUID holdingId,
    UUID productId,
    String productName,
    String ticker,
    UUID accountId,
    String accountName,
    LatestSnapshotResponse latestSnapshot) {

  /** The date and balance of the holding's most recent snapshot. */
  public record LatestSnapshotResponse(LocalDate date, BigDecimal balance) {}

  public static SnapshotSheetRowResponse from(OpenHoldingSnapshotRow row) {
    OpenHoldingSnapshotRow.LatestSnapshot latest = row.latestSnapshot();
    return new SnapshotSheetRowResponse(
        row.holdingId(),
        row.productId(),
        row.productName(),
        row.ticker(),
        row.accountId(),
        row.accountName(),
        latest == null ? null : new LatestSnapshotResponse(latest.date(), latest.balance()));
  }
}
