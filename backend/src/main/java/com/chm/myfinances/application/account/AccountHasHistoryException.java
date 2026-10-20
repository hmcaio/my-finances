package com.chm.myfinances.application.account;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code DELETE /api/accounts/{id}} targets an account that is still referenced by a
 * transaction, transfer, recurring template or investment product (ADR 0017). Maps to 409 - the
 * user closes the account instead.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountHasHistoryException extends RuntimeException {

  public AccountHasHistoryException(UUID accountId) {
    super("Account has history and cannot be deleted, close it instead: " + accountId);
  }
}
