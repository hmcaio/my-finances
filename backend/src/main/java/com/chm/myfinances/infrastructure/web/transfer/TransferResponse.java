package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of a {@link Transfer} (F005 spec). {@code investmentProductId}, {@code
 * quantity}, {@code unitPrice} and {@code taxes} (F009) are {@code null} for a plain transfer; when
 * a product is set the transfer is a buy (into the product's investment account) or a sell (out of
 * it) - the direction is derived from the accounts, never stored.
 */
public record TransferResponse(
    UUID id,
    LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String description,
    String additionalNotes,
    UUID investmentProductId,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal taxes) {

  public static TransferResponse from(Transfer transfer) {
    return new TransferResponse(
        transfer.getId(),
        transfer.getDate(),
        transfer.getFromAccountId(),
        transfer.getToAccountId(),
        transfer.getAmount(),
        transfer.getDescription(),
        transfer.getAdditionalNotes(),
        transfer.getInvestmentProductId(),
        transfer.getTradeDetails().quantity(),
        transfer.getTradeDetails().unitPrice(),
        transfer.getTradeDetails().taxes());
  }
}
