package com.chm.myfinances.application.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code Transaction}'s category and {@code fuelDetails} disagree (F024 spec, ADR
 * 0021): a fuel-category transaction with no {@code fuelDetails}, or a non-fuel-category
 * transaction with {@code fuelDetails}. Maps to 400, not 409 - unlike {@code
 * BuiltInCategoryException}/{@code FuelCategoryException} (whether the request is valid depends on
 * a persisted flag), this is a self-contained request-shape error: given the {@code categoryId} in
 * the request body and whether {@code fuelDetails} is present, the mismatch is knowable without
 * touching any other row, same reasoning as {@code SameAccountTransferException}.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class FuelDetailsCategoryMismatchException extends RuntimeException {

  public FuelDetailsCategoryMismatchException() {
    super("fuelDetails must be present if and only if the category is the fuel category");
  }
}
