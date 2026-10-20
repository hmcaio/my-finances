package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a holding that has history (a snapshot, or a transfer tagged with its
 * product and its account; F022 spec) - the user closes it instead. Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentHoldingHasHistoryException extends RuntimeException {

  public InvestmentHoldingHasHistoryException(UUID id) {
    super("Investment holding has history and cannot be deleted: " + id);
  }
}
