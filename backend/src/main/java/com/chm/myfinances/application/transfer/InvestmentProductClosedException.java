package com.chm.myfinances.application.transfer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a buy/sell {@code Transfer} is created or edited against a closed {@code
 * InvestmentProduct} (F009 spec). Maps to 409 - it depends on the product's persisted state - and
 * gets its own exception per the backend {@code CLAUDE.md} error convention.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductClosedException extends RuntimeException {

  public InvestmentProductClosedException(UUID productId) {
    super("Investment product is closed and cannot take new trades: " + productId);
  }
}
