package com.chm.myfinances.application.goal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a product's percentages across non-archived goals would exceed 100 (F039, ADR 0034):
 * on saving a goal's allocations, or on restoring an archived goal whose percentages no longer fit.
 * Maps to 409, as the conflict is with other goals' persisted state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class GoalAllocationExceedsProductException extends RuntimeException {

  public GoalAllocationExceedsProductException(UUID productId) {
    super("Allocations of the product exceed 100% across goals: " + productId);
  }
}
