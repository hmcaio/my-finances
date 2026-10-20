package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a product's sub-category is not a child of its category (F008 spec). Maps to 409; the
 * composite foreign key on {@code investment_products} backs it.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSubcategoryMismatchException extends RuntimeException {

  public InvestmentSubcategoryMismatchException(UUID subcategoryId) {
    super("Investment sub-category does not belong to the chosen category: " + subcategoryId);
  }
}
