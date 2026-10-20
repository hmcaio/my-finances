package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a lookup by id finds no {@code InvestmentHolding} (F022 spec), or when {@code
 * TransferService} finds no holding for a {@code (productId, accountId)} pair - a trade shouldn't
 * silently create one. Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentHoldingNotFoundException extends RuntimeException {

  public InvestmentHoldingNotFoundException(UUID id) {
    super("Investment holding not found: " + id);
  }

  public InvestmentHoldingNotFoundException(UUID productId, UUID accountId) {
    super("No investment holding exists for product " + productId + " in account " + accountId);
  }
}
