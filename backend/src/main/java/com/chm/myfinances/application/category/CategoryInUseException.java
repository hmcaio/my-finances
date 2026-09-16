package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a {@code Category} that's referenced by at least one {@code Transaction}
 * (F002 plan.md's deferred delete guard, added now that F004's {@code transactions} table exists to
 * check against - see CLAUDE.md's F002 status entry). Maps to 409, same {@code @ResponseStatus}
 * pattern as {@code AccountAlreadyClosedException} (F003).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryInUseException extends RuntimeException {

  public CategoryInUseException(UUID id) {
    super("Category is referenced by at least one transaction and cannot be deleted: " + id);
  }
}
