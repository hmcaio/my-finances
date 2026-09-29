package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a holding would be created against an account that is not an open {@code INVESTMENT}
 * account (F008 spec; reused as-is by {@code InvestmentHoldingService}, F022). Maps to 409 -
 * validity depends on the account's persisted type and state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentAccountRequiredException extends RuntimeException {

  public InvestmentAccountRequiredException(UUID accountId) {
    super("Investment holdings need an open INVESTMENT account: " + accountId);
  }
}
