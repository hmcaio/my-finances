package com.chm.myfinances.application.allocationplan;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an allocation plan's entries reference the same investment product more than once
 * (F026 spec, ADR 0023). Maps to 400 - a self-contained request-shape error.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AllocationPlanDuplicateProductException extends RuntimeException {

  public AllocationPlanDuplicateProductException(UUID productId) {
    super("Allocation plan entries must not reference the same product twice: " + productId);
  }
}
