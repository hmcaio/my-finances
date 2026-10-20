package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of a {@link Transfer} (F005 spec). {@code taxes}/{@code tradeConfirmation}
 * (F027, ADR 0024) are {@code null} for a plain transfer; when a confirmation is set, {@code
 * fromAccountId}/{@code toAccountId}/{@code amount} are the backend-derived settlement (never the
 * client-supplied {@code cashAccountId}/{@code investmentAccountId}, which aren't echoed back as
 * such - the direction they resolved to is what matters).
 */
public record TransferResponse(
    UUID id,
    LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String description,
    String additionalNotes,
    BigDecimal taxes,
    TradeConfirmationResponse tradeConfirmation) {

  public static TransferResponse from(Transfer transfer) {
    return new TransferResponse(
        transfer.getId(),
        transfer.getDate(),
        transfer.getFromAccountId(),
        transfer.getToAccountId(),
        transfer.getAmount(),
        transfer.getDescription(),
        transfer.getAdditionalNotes(),
        transfer.getTaxes(),
        transfer.getTradeConfirmation().map(TradeConfirmationResponse::from).orElse(null));
  }
}
