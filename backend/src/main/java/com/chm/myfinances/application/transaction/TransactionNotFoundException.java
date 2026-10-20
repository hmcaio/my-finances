package com.chm.myfinances.application.transaction;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Transaction} id doesn't resolve to an existing transaction. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TransactionNotFoundException extends RuntimeException {

  public TransactionNotFoundException(UUID id) {
    super("Transaction not found: " + id);
  }
}
