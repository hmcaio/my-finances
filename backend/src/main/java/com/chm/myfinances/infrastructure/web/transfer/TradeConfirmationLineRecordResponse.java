package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of one row of {@code GET /api/trade-confirmation-lines?productId=} (F027
 * spec): one matching line, carrying its parent {@code transferId} so the frontend can link back to
 * the full confirmation. No {@code productId} (it's the query param) and no taxes (the
 * confirmation's one aggregate figure, not attributable to a single line/product).
 */
public record TradeConfirmationLineRecordResponse(
    UUID transferId,
    LocalDate date,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal resultingBalance) {

  public static TradeConfirmationLineRecordResponse from(TransferTradeLine line) {
    return new TradeConfirmationLineRecordResponse(
        line.transferId(),
        line.date(),
        line.side(),
        line.quantity(),
        line.unitPrice(),
        line.resultingBalance());
  }
}
