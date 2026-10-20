package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.TradeConfirmation;
import java.util.List;

/**
 * API representation of a {@link TradeConfirmation} (F027 spec). {@code taxes} is {@code
 * TransferResponse}'s own top-level field, not repeated here.
 */
public record TradeConfirmationResponse(List<TradeConfirmationLineResponse> lines) {

  public static TradeConfirmationResponse from(TradeConfirmation confirmation) {
    return new TradeConfirmationResponse(
        confirmation.getLines().stream().map(TradeConfirmationLineResponse::from).toList());
  }
}
