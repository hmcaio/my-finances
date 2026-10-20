package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a holding is created for a {@code (productId, accountId)} pair that already has one
 * (F022 spec: unique per product+account). Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentHoldingAlreadyExistsException extends RuntimeException {

  public InvestmentHoldingAlreadyExistsException(UUID productId, UUID accountId) {
    super("A holding already exists for product " + productId + " in account " + accountId);
  }
}
