package com.chm.myfinances.application.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when {@code POST /api/reports}' {@code types} list is missing or empty (F030 spec: "An
 * unknown/empty types list is 400"). An unknown enum value is already rejected earlier, by Spring's
 * own JSON deserialization ({@code HttpMessageNotReadableException}, rethrown as 400 by {@code
 * GlobalExceptionHandler}) - this exception only covers the empty-list case. Maps to 400 - the
 * request is malformed regardless of state.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyReportTypesException extends RuntimeException {

  public EmptyReportTypesException() {
    super("At least one report type must be selected");
  }
}
