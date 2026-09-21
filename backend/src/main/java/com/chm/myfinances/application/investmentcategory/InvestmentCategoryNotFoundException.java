package com.chm.myfinances.application.investmentcategory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an {@code InvestmentCategory} id does not resolve to an existing category. Maps to
 * 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentCategoryNotFoundException extends RuntimeException {

  public InvestmentCategoryNotFoundException(UUID id) {
    super("Investment category not found: " + id);
  }
}
