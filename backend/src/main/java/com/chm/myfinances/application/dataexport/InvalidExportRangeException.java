package com.chm.myfinances.application.dataexport;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when the export's {@code dateFrom} is after its {@code dateTo} (F013). Maps to 400 - the
 * request is malformed regardless of state.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidExportRangeException extends RuntimeException {

  public InvalidExportRangeException() {
    super("Invalid export range: dateFrom must not be after dateTo");
  }
}
