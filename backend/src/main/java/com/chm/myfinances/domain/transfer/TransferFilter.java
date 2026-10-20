package com.chm.myfinances.domain.transfer;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filter dimensions for {@link TransferRepository#findAll} (F005 spec's {@code GET
 * /api/transfers?dateFrom=&dateTo=&accountId=}). Every field is nullable - a {@code null} field
 * means "no constraint on this dimension". {@code accountId} matches either side of the transfer
 * (PRD S6.9's export filter semantics mirror this) - it isn't split into separate from/to filters.
 * {@code investmentProductId} (F009) narrows to one product's buys/sells. {@code searchText} (F037)
 * is raw user text matched against {@code description} or {@code additionalNotes}, ignoring case
 * and accents (ADR 0030); the persistence adapter escapes it. A blank value means no search.
 */
public record TransferFilter(
    LocalDate dateFrom,
    LocalDate dateTo,
    UUID accountId,
    UUID investmentProductId,
    String searchText) {

  /** The pre-F037 shape: no text search. */
  public TransferFilter(
      LocalDate dateFrom, LocalDate dateTo, UUID accountId, UUID investmentProductId) {
    this(dateFrom, dateTo, accountId, investmentProductId, null);
  }

  /** No filtering at all - every transfer matches. */
  public static TransferFilter none() {
    return new TransferFilter(null, null, null, null);
  }

  /** Whether {@link #searchText} narrows the result (non-null and not blank). */
  public boolean hasSearchText() {
    return searchText != null && !searchText.isBlank();
  }
}
