package com.chm.myfinances.application.investmentreport;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the value series is requested for a reversed or excessively long month range (F009
 * spec). Maps to 400 - the request is malformed regardless of state.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidValueSeriesRangeException extends RuntimeException {

  public InvalidValueSeriesRangeException(String reason) {
    super("Invalid value series range: " + reason);
  }
}
