package com.chm.myfinances.application.category;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting or renaming the dedicated dividend category (F026 spec, ADR 0023).
 * Independent of {@link BuiltInCategoryException}/{@link FuelCategoryException} - {@code
 * dividend_category}, {@code fuel_category} and {@code built_in} are three separate flags a
 * category can carry, never assumed to mean the same thing. Same "delete and rename both blocked"
 * shape as {@code FuelCategoryException}: the dividend history view's grouping depends on this
 * category's identity staying stable, not just its existence. Maps to 409 (not 400): whether the
 * request is valid depends on the persisted row's {@code dividendCategory} flag, not the request's
 * shape.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DividendCategoryException extends RuntimeException {

  public DividendCategoryException(UUID id) {
    super("The dividend category cannot be deleted or renamed: " + id);
  }
}
