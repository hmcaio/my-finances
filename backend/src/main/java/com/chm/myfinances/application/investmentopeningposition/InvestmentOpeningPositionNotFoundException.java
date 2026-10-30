package com.chm.myfinances.application.investmentopeningposition;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a holding has no opening position to read or delete (F038 spec, ADR 0033). Maps to
 * 404, with its own problem type so the frontend can tell "no position yet" (the empty state) from
 * {@code InvestmentHoldingNotFoundException} (an unknown holding).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentOpeningPositionNotFoundException extends RuntimeException {

  public InvestmentOpeningPositionNotFoundException(UUID holdingId) {
    super("Investment holding has no opening position: " + holdingId);
  }
}
