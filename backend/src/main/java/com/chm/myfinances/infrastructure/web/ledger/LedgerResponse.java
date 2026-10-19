package com.chm.myfinances.infrastructure.web.ledger;

import com.chm.myfinances.application.ledger.Ledger;
import java.util.List;

/**
 * API representation of {@link Ledger} (F037): the entries newest first, the summary of the whole
 * filtered scope, and whether more rows matched than {@code entries} holds.
 */
public record LedgerResponse(
    List<LedgerEntryResponse> entries, LedgerSummaryResponse summary, boolean truncated) {

  public static LedgerResponse from(Ledger ledger) {
    return new LedgerResponse(
        ledger.entries().stream().map(LedgerEntryResponse::from).toList(),
        LedgerSummaryResponse.from(ledger.summary()),
        ledger.truncated());
  }
}
