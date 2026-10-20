package com.chm.myfinances.application.investmentsnapshot;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when editing a snapshot to a date on which the holding already has another snapshot (one
 * per holding per date, PRD S5.8, F022). Maps to 409 - the user edits or deletes the other one
 * first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSnapshotDateTakenException extends RuntimeException {

  public InvestmentSnapshotDateTakenException(UUID holdingId) {
    super("Investment holding already has a snapshot on that date: " + holdingId);
  }
}
