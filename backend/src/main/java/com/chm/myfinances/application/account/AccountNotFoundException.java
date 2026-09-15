package com.chm.myfinances.application.account;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an {@code Account} id doesn't resolve to an existing account. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException(UUID id) {
    super("Account not found: " + id);
  }
}
