package com.chm.myfinances.application.investmentsubcategory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an {@code InvestmentSubcategory} id does not resolve to an existing sub-category.
 * Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentSubcategoryNotFoundException extends RuntimeException {

  public InvestmentSubcategoryNotFoundException(UUID id) {
    super("Investment sub-category not found: " + id);
  }
}
