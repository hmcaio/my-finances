package com.chm.myfinances.application.allocationplan;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an {@code AllocationPlanEntry}'s product isn't classified under the "REITs (FIIs)"
 * sub-category (F026 spec, ADR 0023). Maps to 409 - whether the request is valid depends on the
 * product's persisted classification, not the request's own shape.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class AllocationPlanEntryNotFiiException extends RuntimeException {

  public AllocationPlanEntryNotFiiException(UUID productId) {
    super("Investment product is not classified under the FII sub-category: " + productId);
  }
}
