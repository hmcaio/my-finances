package com.chm.myfinances.application.recurringtemplate;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code RecurringTemplate} lookup by id finds nothing (F007 spec). Follows the
 * {@code {Aggregate}NotFoundException} convention established in F002 - {@code
 * GlobalExceptionHandler} rethrows it unmodified since it already carries {@link ResponseStatus}.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecurringTemplateNotFoundException extends RuntimeException {

  public RecurringTemplateNotFoundException(UUID id) {
    super("RecurringTemplate not found: " + id);
  }
}
