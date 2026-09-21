package com.chm.myfinances.application.investmentsubcategory;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming an {@code InvestmentSubcategory} to a name a sibling under the
 * same category already has - unique per category, not globally (F008 spec). Maps to 409; backed by
 * {@code UNIQUE (investment_category_id, name)} as defense in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSubcategoryNameAlreadyExistsException extends RuntimeException {

  public InvestmentSubcategoryNameAlreadyExistsException(String name) {
    super("An investment sub-category named '" + name + "' already exists in this category");
  }
}
