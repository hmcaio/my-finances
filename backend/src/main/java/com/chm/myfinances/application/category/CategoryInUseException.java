package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a {@code Category} that's still referenced elsewhere (F002 plan.md's
 * deferred delete guard, added now that F004's {@code transactions} table exists to check against -
 * see F004's spec.md; broadened in the post-F007 schema audit to also cover F006's {@code Budget}
 * and F007's {@code RecurringTemplate}, both of which FK-reference {@code categories}). Maps to
 * 409, same {@code @ResponseStatus} pattern as {@code AccountAlreadyClosedException} (F003).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryInUseException extends RuntimeException {

  public CategoryInUseException(UUID id) {
    super(
        "Category is referenced by at least one transaction, budget, or recurring template and"
            + " cannot be deleted: "
            + id);
  }
}
