package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.DividendRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a {@link DividendRow} (F026 spec). */
public record DividendRowResponse(
    UUID transactionId,
    LocalDate date,
    BigDecimal amount,
    UUID investmentHoldingId,
    UUID productId,
    String ticker,
    String productName,
    String description) {

  public static DividendRowResponse from(DividendRow row) {
    return new DividendRowResponse(
        row.transactionId(),
        row.date(),
        row.amount(),
        row.investmentHoldingId(),
        row.productId(),
        row.ticker(),
        row.productName(),
        row.description());
  }
}
