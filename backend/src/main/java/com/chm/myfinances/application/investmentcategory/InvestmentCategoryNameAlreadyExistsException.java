package com.chm.myfinances.application.investmentcategory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming an {@code InvestmentCategory} to a name another category already
 * has (exact match, case-sensitive, like every other named entity). Maps to 409; backed by the
 * {@code UNIQUE} on {@code investment_categories.name} as defense in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentCategoryNameAlreadyExistsException extends RuntimeException {

  public InvestmentCategoryNameAlreadyExistsException(String name) {
    super("An investment category named '" + name + "' already exists");
  }
}
