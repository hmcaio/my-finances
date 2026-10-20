package com.chm.myfinances.application.account;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/accounts/{id}/close} targets an account that's already closed. Maps
 * to 409 - a new *expected* error case gets its own {@code @ResponseStatus} exception per the
 * {@code CategoryNotFoundException}/{@code AccountNotFoundException} pattern, rather than a second
 * catch-all advice (see {@code GlobalExceptionHandler}'s javadoc).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountAlreadyClosedException extends RuntimeException {

  public AccountAlreadyClosedException(UUID id) {
    super("Account is already closed: " + id);
  }
}
