package com.chm.myfinances.application.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code Transaction}'s category and {@code investmentHoldingId} disagree (F026 spec,
 * ADR 0023): a dividend-category transaction with no {@code investmentHoldingId}, or a
 * non-dividend-category transaction with one. Maps to 400, not 409 - same reasoning as {@code
 * FuelDetailsCategoryMismatchException}: given the {@code categoryId} in the request body and
 * whether {@code investmentHoldingId} is present, the mismatch is knowable without touching any
 * other row.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvestmentHoldingCategoryMismatchException extends RuntimeException {

  public InvestmentHoldingCategoryMismatchException() {
    super(
        "investmentHoldingId must be present if and only if the category is the dividend category");
  }
}
