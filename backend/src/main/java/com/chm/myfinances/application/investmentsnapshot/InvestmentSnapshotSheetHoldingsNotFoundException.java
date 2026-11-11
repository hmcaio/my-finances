package com.chm.myfinances.application.investmentsnapshot;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown by {@link InvestmentSnapshotService#recordBatch} before any write when one or more entries
 * name a holding that does not exist (deleted in another tab, say). Carries the index of EVERY
 * offending entry, so the snapshot sheet can highlight all of them at once; {@code
 * GlobalExceptionHandler} turns the indexes into the {@code errors[]} member of the 404 body. Its
 * message carries a count only, never a holding id or an amount (ADR 0011).
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentSnapshotSheetHoldingsNotFoundException extends RuntimeException {

  private final List<Integer> entryIndexes;

  public InvestmentSnapshotSheetHoldingsNotFoundException(List<Integer> entryIndexes) {
    super("Investment holdings not found for " + entryIndexes.size() + " snapshot sheet entries");
    this.entryIndexes = List.copyOf(entryIndexes);
  }

  /** Zero-based indexes into the request's {@code entries}, ascending. */
  public List<Integer> entryIndexes() {
    return entryIndexes;
  }
}
