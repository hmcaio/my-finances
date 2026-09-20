package com.chm.myfinances.application.institution;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code Institution} that an account still references. Closed accounts
 * count: accounts are never deleted, so the way out is re-pointing every account and deleting the
 * then-empty institution. Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InstitutionInUseException extends RuntimeException {

  public InstitutionInUseException(UUID id) {
    super("Institution is referenced by at least one account and cannot be deleted: " + id);
  }
}
