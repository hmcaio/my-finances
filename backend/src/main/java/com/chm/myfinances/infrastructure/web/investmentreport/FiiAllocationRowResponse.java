package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.FiiAllocationRow;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of a {@link FiiAllocationRow} (F026 spec). {@code segmentId} (Addendum -
 * Nested Allocation Charts) is set on a {@code TICKER}-groupBy row, {@code null} on a {@code
 * SEGMENT}-groupBy row.
 */
public record FiiAllocationRowResponse(
    UUID key, String label, BigDecimal totalValue, BigDecimal percentage, UUID segmentId) {

  public static FiiAllocationRowResponse from(FiiAllocationRow row) {
    return new FiiAllocationRowResponse(
        row.key(), row.label(), row.totalValue(), row.percentage(), row.segmentId());
  }
}
