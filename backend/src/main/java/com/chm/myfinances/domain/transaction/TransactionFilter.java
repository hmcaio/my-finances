package com.chm.myfinances.domain.transaction;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filter dimensions for {@link TransactionRepository#findAll} (F004 spec's {@code GET
 * /api/transactions?dateFrom=&dateTo=&categoryId=&accountId=&paymentMethodId=}). Every field is
 * nullable - a {@code null} field means "no constraint on this dimension" - and any combination,
 * including none set at all, is valid.
 */
public record TransactionFilter(
    LocalDate dateFrom, LocalDate dateTo, UUID categoryId, UUID accountId, UUID paymentMethodId) {

  /** No filtering at all - every transaction matches. */
  public static TransactionFilter none() {
    return new TransactionFilter(null, null, null, null, null);
  }
}
