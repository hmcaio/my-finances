package com.chm.myfinances.application.transaction;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or editing a {@code Transaction} would post/move it against a closed {@code
 * Account}. Maps to 409 - a new *expected* error case gets its own {@code @ResponseStatus}
 * exception per the {@code AccountAlreadyClosedException} pattern (F003), rather than a second
 * catch-all advice (see {@code GlobalExceptionHandler}'s javadoc).
 *
 * <p>{@code Account.requireOpen()} (F003 spec) is the actual domain-level guard - this exception is
 * how the application layer translates that guard's generic {@code IllegalStateException} into a
 * proper HTTP status for this feature's callers.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountClosedException extends RuntimeException {

  public AccountClosedException(UUID accountId) {
    super("Account is closed and cannot accept new activity: " + accountId);
  }
}
