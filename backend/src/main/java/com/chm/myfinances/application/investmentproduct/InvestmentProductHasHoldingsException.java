package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentProduct} that has snapshots or buy/sell transfers (PRD
 * S5.8 delete-safety rule; F009 supplies the history). Maps to 409, directing the user to close it.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductHasHistoryException extends RuntimeException {

  public InvestmentProductHasHistoryException(UUID id) {
    super("Investment product has history and cannot be deleted, close it instead: " + id);
  }
}
