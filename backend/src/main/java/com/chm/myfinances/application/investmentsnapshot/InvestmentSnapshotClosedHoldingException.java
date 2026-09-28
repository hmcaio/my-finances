package com.chm.myfinances.application.investmentsnapshot;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when editing or deleting a snapshot would leave a closed product's latest snapshot
 * non-zero (PRD S5.8: a closed product never keeps counting a stale value). Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSnapshotClosedProductException extends RuntimeException {

  public InvestmentSnapshotClosedProductException(UUID productId) {
    super("Change would leave a closed investment product with value: " + productId);
  }
}
