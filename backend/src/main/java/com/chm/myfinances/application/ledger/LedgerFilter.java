package com.chm.myfinances.application.ledger;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter for {@link LedgerQuery#find} (F037). Every component is nullable - {@code null} means "no
 * constraint on this dimension". {@code searchText} is raw user text (the adapters escape it); it
 * is trimmed here and a blank value becomes {@code null}, i.e. "no search".
 */
public record LedgerFilter(
    LocalDate dateFrom,
    LocalDate dateTo,
    LedgerEntryType type,
    UUID accountId,
    UUID categoryId,
    UUID paymentMethodId,
    String searchText) {

  public LedgerFilter {
    if (searchText != null) {
      String trimmed = searchText.trim();
      searchText = trimmed.isEmpty() ? null : trimmed;
    }
  }

  /** No filtering at all. */
  public static LedgerFilter none() {
    return new LedgerFilter(null, null, null, null, null, null, null);
  }
}
