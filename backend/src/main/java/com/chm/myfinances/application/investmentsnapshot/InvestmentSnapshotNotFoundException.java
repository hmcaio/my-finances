package com.chm.myfinances.application.investmentsnapshot;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a snapshot id does not resolve to a snapshot of the product in the path (unknown, or
 * it belongs to another product). Maps to 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentSnapshotNotFoundException extends RuntimeException {

  public InvestmentSnapshotNotFoundException(UUID id) {
    super("Investment snapshot not found: " + id);
  }
}
