package com.chm.myfinances.application.budget;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating a {@code Budget} against an {@code INCOME} category (F006 spec: "must
 * reference an EXPENSE-type category - reject creation against an INCOME category", PRD S5.6). Maps
 * to 409, same reasoning as {@code TransferService}'s {@code AccountClosedException}: whether this
 * request is valid depends entirely on another aggregate's already-persisted state (the category's
 * {@code type}, immutable per F002), not on anything wrong with the request payload in isolation -
 * a state conflict, not malformed input.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CategoryNotExpenseException extends RuntimeException {

  public CategoryNotExpenseException(UUID categoryId) {
    super("Category is not an expense category and cannot be budgeted: " + categoryId);
  }
}
