package com.chm.myfinances.infrastructure.web.investmentholding;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code GET /api/investment-holdings} is called with neither or both of {@code
 * ?productId=}/{@code ?accountId=} (F022 spec: exactly one is required). Maps to 400 - malformed
 * regardless of persisted state, so it lives beside the controller rather than in application code.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvestmentHoldingFilterRequiredException extends RuntimeException {

  public InvestmentHoldingFilterRequiredException() {
    super("Exactly one of productId or accountId is required");
  }
}
