package com.chm.myfinances.application.ledger;

import java.util.List;

/**
 * The Ledger read model (F037): the newest-first entries (at most {@link LedgerQuery#MAX_ENTRIES}),
 * the summary of the whole filtered scope, and whether more rows matched than the entries show.
 */
public record Ledger(List<LedgerEntry> entries, LedgerSummary summary, boolean truncated) {}
