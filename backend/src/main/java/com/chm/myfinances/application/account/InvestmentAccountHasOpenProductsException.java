package com.chm.myfinances.application.account;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/accounts/{id}/close} targets an {@code INVESTMENT} account that
 * still has an open product (F008 spec). Maps to 409 - closing depends on another aggregate's
 * persisted state; the user closes the products first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentAccountHasOpenProductsException extends RuntimeException {

  public InvestmentAccountHasOpenProductsException(UUID accountId) {
    super("Investment account still has open products and cannot be closed: " + accountId);
  }
}
