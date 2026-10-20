package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Category} id doesn't resolve to an existing category. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CategoryNotFoundException extends RuntimeException {

  public CategoryNotFoundException(UUID id) {
    super("Category not found: " + id);
  }
}
