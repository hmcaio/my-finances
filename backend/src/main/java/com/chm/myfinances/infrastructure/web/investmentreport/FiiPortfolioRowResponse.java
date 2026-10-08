package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.FiiPortfolioRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a {@link FiiPortfolioRow} (F026 spec). */
public record FiiPortfolioRowResponse(
    UUID productId,
    String ticker,
    String name,
    UUID segmentId,
    BigDecimal cotasHeld,
    BigDecimal amountContributed,
    BigDecimal currentValue,
    LocalDate latestSnapshotDate,
    boolean needsSnapshot,
    boolean hasOpenHolding) {

  public static FiiPortfolioRowResponse from(FiiPortfolioRow row) {
    return new FiiPortfolioRowResponse(
        row.productId(),
        row.ticker(),
        row.name(),
        row.segmentId(),
        row.cotasHeld(),
        row.amountContributed(),
        row.currentValue(),
        row.latestSnapshotDate(),
        row.needsSnapshot(),
        row.hasOpenHolding());
  }
}
