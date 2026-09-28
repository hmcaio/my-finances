package com.chm.myfinances.application.account;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/accounts/{id}/close} targets an {@code INVESTMENT} account that
 * still has an open holding (F008 spec, moved from product to holding by F022/ADR 0020). Maps to
 * 409 - closing depends on another aggregate's persisted state; the user closes the holdings first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentAccountHasOpenHoldingsException extends RuntimeException {

  public InvestmentAccountHasOpenHoldingsException(UUID accountId) {
    super("Investment account still has open holdings and cannot be closed: " + accountId);
  }
}
