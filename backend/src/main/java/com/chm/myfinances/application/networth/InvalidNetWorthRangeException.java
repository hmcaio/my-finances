package com.chm.myfinances.application.networth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the net worth trend is requested for a reversed or excessively long date range (F010
 * spec). Maps to 400 - the request is malformed regardless of state.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidNetWorthRangeException extends RuntimeException {

  public InvalidNetWorthRangeException(String reason) {
    super("Invalid net worth range: " + reason);
  }
}
