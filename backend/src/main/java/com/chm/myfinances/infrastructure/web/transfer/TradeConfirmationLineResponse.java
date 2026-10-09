package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * API representation of one {@link TradeConfirmationLine} (F027 spec). Product names are resolved
 * client-side from the already-loaded products list, same as the holding picker does today - this
 * carries only {@code productId}.
 */
public record TradeConfirmationLineResponse(
    UUID productId,
    TradeSide side,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal resultingBalance,
    boolean closeHolding) {

  public static TradeConfirmationLineResponse from(TradeConfirmationLine line) {
    return new TradeConfirmationLineResponse(
        line.productId(),
        line.side(),
        line.quantity(),
        line.unitPrice(),
        line.resultingBalance(),
        line.closeHolding());
  }
}
