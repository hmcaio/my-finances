package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a built-in category ("Other Expense" / "Other Income" by default). Maps to
 * 409 (not 400): whether the request is valid depends on the persisted row's {@code builtIn} flag,
 * not on the request's shape.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BuiltInCategoryException extends RuntimeException {

  public BuiltInCategoryException(UUID id) {
    super("A built-in category cannot be deleted: " + id);
  }
}
