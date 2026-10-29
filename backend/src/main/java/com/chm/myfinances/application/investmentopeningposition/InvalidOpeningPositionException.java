package com.chm.myfinances.application.investmentopeningposition;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for an opening position that is wrong regardless of what is persisted (F038 spec, ADR
 * 0033): an as-of date in the future, or a value the domain rejects (non-positive quantity or unit
 * price, a null field) - the service translates the domain's {@link IllegalArgumentException}, as
 * F027's {@code InvalidTradeConfirmationException} does. Maps to 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidOpeningPositionException extends RuntimeException {

  public InvalidOpeningPositionException(String reason) {
    super("Invalid opening position: " + reason);
  }
}
