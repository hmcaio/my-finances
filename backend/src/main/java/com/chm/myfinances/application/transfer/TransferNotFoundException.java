package com.chm.myfinances.application.transfer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Transfer} id doesn't resolve to an existing transfer. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TransferNotFoundException extends RuntimeException {

  public TransferNotFoundException(UUID id) {
    super("Transfer not found: " + id);
  }
}
