package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when closing an {@code InvestmentProduct} whose latest snapshot is non-zero (F009 spec's
 * close guard): a closed product would keep counting a stale value in the account balance and the
 * allocation. Maps to 409 - the user records a zero snapshot (or sells the entire position) first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductNotEmptyException extends RuntimeException {

  public InvestmentProductNotEmptyException(UUID id) {
    super("Investment product still has value and cannot be closed: " + id);
  }
}
