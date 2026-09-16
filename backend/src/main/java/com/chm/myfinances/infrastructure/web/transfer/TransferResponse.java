package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a {@link Transfer} (F005 spec). */
public record TransferResponse(
    UUID id,
    LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    BigDecimal amount,
    String description,
    String additionalNotes) {

  public static TransferResponse from(Transfer transfer) {
    return new TransferResponse(
        transfer.getId(),
        transfer.getDate(),
        transfer.getFromAccountId(),
        transfer.getToAccountId(),
        transfer.getAmount(),
        transfer.getDescription(),
        transfer.getAdditionalNotes());
  }
}
