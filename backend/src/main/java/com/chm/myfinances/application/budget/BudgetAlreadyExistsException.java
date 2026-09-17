package com.chm.myfinances.application.budget;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating a {@code Budget} for a category that already has one (PRD S5.6: "Budget (one
 * per category)"). Maps to 409 - a new *expected* error case gets its own {@code @ResponseStatus}
 * exception per the {@code CategoryInUseException}/{@code AccountClosedException} pattern, rather
 * than a second catch-all advice (see {@code GlobalExceptionHandler}'s javadoc).
 *
 * <p>Backed by {@code budgets.category_id UNIQUE} (F006's {@code V8__budgets.sql}) as defense in
 * depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class BudgetAlreadyExistsException extends RuntimeException {

  public BudgetAlreadyExistsException(UUID categoryId) {
    super("A budget already exists for category: " + categoryId);
  }
}
