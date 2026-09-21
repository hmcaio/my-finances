package com.chm.myfinances.application.investmentcategory;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentCategory} that still has sub-categories or is used by a
 * product (F008 spec, same pattern as F002's {@code CategoryInUseException}). Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentCategoryInUseException extends RuntimeException {

  public InvestmentCategoryInUseException(UUID id) {
    super("Investment category still has sub-categories or products and cannot be deleted: " + id);
  }
}
