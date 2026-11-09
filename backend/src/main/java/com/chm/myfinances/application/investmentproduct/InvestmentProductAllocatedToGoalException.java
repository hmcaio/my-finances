package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentProduct} that any goal still allocates (F039, ADR 0034;
 * PRD S5.8: a product is hard-deleted only with zero holdings and no goal allocation). Closing the
 * product is unaffected. Maps to 409, so the delete never reaches the foreign key as a 500.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductAllocatedToGoalException extends RuntimeException {

  public InvestmentProductAllocatedToGoalException(UUID id) {
    super("Investment product is allocated to a goal and cannot be deleted: " + id);
  }
}
