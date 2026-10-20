package com.chm.myfinances.application.institution;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming an {@code Institution} to a name another institution already has
 * (exact match, case-sensitive - matches Postgres's own default {@code UNIQUE} semantics). Maps to
 * 409, same pattern as {@code CategoryNameAlreadyExistsException}.
 *
 * <p>Backed by {@code institutions.name UNIQUE} ({@code V12__institutions.sql}) as defense in
 * depth. It also means nobody can create a second "No institution" by name.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InstitutionNameAlreadyExistsException extends RuntimeException {

  public InstitutionNameAlreadyExistsException(String name) {
    super("An institution named '" + name + "' already exists");
  }
}
