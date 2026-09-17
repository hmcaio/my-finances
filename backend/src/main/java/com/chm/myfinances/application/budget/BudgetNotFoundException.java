package com.chm.myfinances.application.budget;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Budget} id doesn't resolve to an existing budget. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BudgetNotFoundException extends RuntimeException {

  public BudgetNotFoundException(UUID id) {
    super("Budget not found: " + id);
  }
}
