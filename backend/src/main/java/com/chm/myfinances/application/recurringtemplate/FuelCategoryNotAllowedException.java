package com.chm.myfinances.application.recurringtemplate;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code RecurringTemplate} would target the fuel category (F024, ADR 0021): a fuel
 * purchase's details (vehicle, fuel type, liters, price, odometer) vary per fill-up and have no
 * templatable field, so there is no way to generate a valid confirmed transaction from a pending
 * occurrence against this category. Maps to 409 - validity depends on the category's persisted flag
 * - beside this package's {@code AccountTypeNotAllowedException}, which rejects an {@code
 * INVESTMENT} account for the same "nothing to template" reason.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class FuelCategoryNotAllowedException extends RuntimeException {

  public FuelCategoryNotAllowedException(UUID categoryId) {
    super("The fuel category cannot be used by a recurring template: " + categoryId);
  }
}
