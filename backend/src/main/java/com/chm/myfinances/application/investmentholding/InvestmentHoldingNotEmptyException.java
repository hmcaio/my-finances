package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when closing a holding whose latest snapshot is non-zero (F022 spec, same rule F009 had on
 * the product): a closed holding must never keep counting a stale value. Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentHoldingNotEmptyException extends RuntimeException {

  public InvestmentHoldingNotEmptyException(UUID id) {
    super("Investment holding still has a non-zero value and cannot be closed: " + id);
  }
}
