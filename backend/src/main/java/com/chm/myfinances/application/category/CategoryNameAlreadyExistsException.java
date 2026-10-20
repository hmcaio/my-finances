package com.chm.myfinances.application.category;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming a {@code Category} to a name another category already has (exact
 * match, case-sensitive - matches Postgres's own default {@code UNIQUE} semantics). Maps to 409,
 * same {@code @ResponseStatus} pattern as {@code BudgetAlreadyExistsException}.
 *
 * <p>Backed by {@code categories.name UNIQUE} ({@code V10__db_constraint_hardening.sql}) as defense
 * in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryNameAlreadyExistsException extends RuntimeException {

  public CategoryNameAlreadyExistsException(String name) {
    super("A category named '" + name + "' already exists");
  }
}
