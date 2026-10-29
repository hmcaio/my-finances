package com.chm.myfinances.infrastructure.web.investmentopeningposition;

import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of an {@link InvestmentOpeningPosition} (F038 spec, ADR 0033). {@code
 * amountContributed} is {@code quantity x unitPrice}, the same record-only arithmetic a buy line
 * uses. The current value is not part of it: it lives in the holding's snapshots.
 */
public record OpeningPositionResponse(
    UUID id,
    UUID holdingId,
    LocalDate asOfDate,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal amountContributed) {

  public static OpeningPositionResponse from(InvestmentOpeningPosition position) {
    return new OpeningPositionResponse(
        position.getId(),
        position.getHoldingId(),
        position.getAsOfDate(),
        position.getQuantity(),
        position.getUnitPrice(),
        position.amountContributed());
  }
}
