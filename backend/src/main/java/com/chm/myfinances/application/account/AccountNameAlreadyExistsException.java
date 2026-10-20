package com.chm.myfinances.application.account;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or editing an {@code Account} to a name another account already has (exact
 * match, case-sensitive - matches Postgres's own default {@code UNIQUE} semantics). Maps to 409,
 * same {@code @ResponseStatus} pattern as {@code CategoryNameAlreadyExistsException}.
 *
 * <p>Backed by {@code accounts.name UNIQUE} ({@code V10__db_constraint_hardening.sql}) as defense
 * in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AccountNameAlreadyExistsException extends RuntimeException {

  public AccountNameAlreadyExistsException(String name) {
    super("An account named '" + name + "' already exists");
  }
}
