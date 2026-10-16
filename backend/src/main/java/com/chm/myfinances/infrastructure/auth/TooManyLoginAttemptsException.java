package com.chm.myfinances.infrastructure.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by the login endpoint while the caller's address is in a {@link LoginAttemptTracker}
 * cooldown (F036, ADR 0029). An ordinary {@code @ResponseStatus} exception, so {@code
 * GlobalExceptionHandler} derives its {@code too-many-login-attempts} type and title from the class
 * name like every other expected error - no new handler.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class TooManyLoginAttemptsException extends RuntimeException {

  public TooManyLoginAttemptsException() {
    super("Too many failed login attempts");
  }
}
