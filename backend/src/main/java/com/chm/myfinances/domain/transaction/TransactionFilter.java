package com.chm.myfinances.domain.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filter dimensions for {@link TransactionRepository#findAll} (F004 spec's {@code GET
 * /api/transactions?dateFrom=&dateTo=&categoryId=&accountId=&paymentMethodId=}). Every field is
 * nullable - a {@code null} field means "no constraint on this dimension" - and any combination,
 * including none set at all, is valid.
 *
 * <p>F037 adds two dimensions for the Ledger: {@code type} restricts to income or expense
 * transactions, and {@code searchText} is raw user text matched against {@code description} or
 * {@code additionalNotes}, ignoring case and accents (ADR 0030); the persistence adapter escapes
 * it, so the text here is never a pattern. A blank {@code searchText} means no search.
 */
public record TransactionFilter(
    LocalDate dateFrom,
    LocalDate dateTo,
    UUID categoryId,
    UUID accountId,
    UUID paymentMethodId,
    CategoryType type,
    String searchText) {

  /** The pre-F037 shape: no type and no text search. */
  public TransactionFilter(
      LocalDate dateFrom, LocalDate dateTo, UUID categoryId, UUID accountId, UUID paymentMethodId) {
    this(dateFrom, dateTo, categoryId, accountId, paymentMethodId, null, null);
  }

  /** No filtering at all - every transaction matches. */
  public static TransactionFilter none() {
    return new TransactionFilter(null, null, null, null, null);
  }

  /** Whether {@link #searchText} narrows the result (non-null and not blank). */
  public boolean hasSearchText() {
    return searchText != null && !searchText.isBlank();
  }
}
