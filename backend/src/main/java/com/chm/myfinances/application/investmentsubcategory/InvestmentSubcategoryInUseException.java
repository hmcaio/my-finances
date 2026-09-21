package com.chm.myfinances.application.investmentsubcategory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentSubcategory} that any product references (F008 spec).
 * Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSubcategoryInUseException extends RuntimeException {

  public InvestmentSubcategoryInUseException(UUID id) {
    super("Investment sub-category is used by at least one product and cannot be deleted: " + id);
  }
}
