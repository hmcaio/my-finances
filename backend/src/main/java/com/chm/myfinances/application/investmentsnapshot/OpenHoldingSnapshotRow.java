package com.chm.myfinances.application.investmentsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the snapshot sheet (F040): an open holding with the names the sheet shows and its most
 * recent snapshot, if it has one.
 */
public record OpenHoldingSnapshotRow(
    UUID holdingId,
    UUID productId,
    String productName,
    String ticker,
    UUID accountId,
    String accountName,
    LatestSnapshot latestSnapshot) {

  /** The date and balance of the holding's most recent snapshot. */
  public record LatestSnapshot(LocalDate date, BigDecimal balance) {}
}
