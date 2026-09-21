package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/investment-products/{id}/close} targets an already-closed product.
 * Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductAlreadyClosedException extends RuntimeException {

  public InvestmentProductAlreadyClosedException(UUID id) {
    super("Investment product is already closed: " + id);
  }
}
