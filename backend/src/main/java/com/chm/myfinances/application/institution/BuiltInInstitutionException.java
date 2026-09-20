package com.chm.myfinances.application.institution;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting the built-in "No institution" row. Maps to 409 (not 400): whether the
 * request is valid depends on the persisted row's {@code builtIn} flag, not on the request's shape.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BuiltInInstitutionException extends RuntimeException {

  public BuiltInInstitutionException(UUID id) {
    super("The built-in institution cannot be deleted: " + id);
  }
}
