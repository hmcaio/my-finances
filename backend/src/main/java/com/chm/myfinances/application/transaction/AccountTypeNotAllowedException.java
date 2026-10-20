package com.chm.myfinances.application.transaction;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code Transaction} would be posted to an {@code INVESTMENT} account (F008, ADR
 * 0012): money moves in and out of an investment account through transfers only (F009). Maps to 409
 * - validity depends on the account's persisted type - beside this package's {@code
 * AccountClosedException}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountTypeNotAllowedException extends RuntimeException {

  public AccountTypeNotAllowedException(UUID accountId) {
    super("An investment account cannot take transactions or recurring templates: " + accountId);
  }
}
