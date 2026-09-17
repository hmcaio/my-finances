package com.chm.myfinances.application.recurringtemplate;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating a {@code RecurringTemplate} against a closed {@code Account}, or confirming
 * a pending occurrence with an overridden {@code accountId} that's closed. Maps to 409 - this
 * feature's own copy of the {@code AccountClosedException} pattern F004/F005 each established
 * (rather than reusing either's package-private-adjacent class directly), per {@code
 * GlobalExceptionHandler}'s "a new expected error case gets its own {@code @ResponseStatus}
 * exception" convention.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountClosedException extends RuntimeException {

  public AccountClosedException(UUID accountId) {
    super("Account is closed and cannot accept new activity: " + accountId);
  }
}
