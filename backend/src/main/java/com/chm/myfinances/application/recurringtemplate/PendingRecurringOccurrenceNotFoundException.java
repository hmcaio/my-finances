package com.chm.myfinances.application.recurringtemplate;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code PendingRecurringOccurrence} lookup by id finds nothing - e.g. confirming or
 * dismissing an id that was already confirmed/dismissed, or never existed (F007 spec).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PendingRecurringOccurrenceNotFoundException extends RuntimeException {

  public PendingRecurringOccurrenceNotFoundException(UUID id) {
    super("PendingRecurringOccurrence not found: " + id);
  }
}
