package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting or renaming the dedicated fuel category (F024 spec, ADR 0021). Independent
 * of {@link BuiltInCategoryException} - {@code fuel_category} and {@code built_in} are two separate
 * flags a category can carry, never assumed to mean the same thing - and stricter: unlike a
 * built-in category, the fuel category can't be renamed either, since it's structurally
 * load-bearing for the {@code Transaction.fuelDetails} invariant. Maps to 409 (not 400): whether
 * the request is valid depends on the persisted row's {@code fuelCategory} flag, not the request's
 * shape.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class FuelCategoryException extends RuntimeException {

  public FuelCategoryException(UUID id) {
    super("The fuel category cannot be deleted or renamed: " + id);
  }
}
