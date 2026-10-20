package com.chm.myfinances.application.institution;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an {@code Institution} id doesn't resolve to an existing institution. Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InstitutionNotFoundException extends RuntimeException {

  public InstitutionNotFoundException(UUID id) {
    super("Institution not found: " + id);
  }
}
