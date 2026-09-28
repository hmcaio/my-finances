package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when closing an already-closed {@code InvestmentHolding} (F022 spec). Maps to 409. */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentHoldingAlreadyClosedException extends RuntimeException {

  public InvestmentHoldingAlreadyClosedException(UUID id) {
    super("Investment holding is already closed: " + id);
  }
}
