package com.chm.myfinances.application.investmentsnapshot;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when editing or deleting a snapshot would leave a closed holding's latest snapshot
 * non-zero (PRD S5.8, F022: a closed holding never keeps counting a stale value). Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSnapshotClosedHoldingException extends RuntimeException {

  public InvestmentSnapshotClosedHoldingException(UUID holdingId) {
    super("Change would leave a closed investment holding with value: " + holdingId);
  }
}
