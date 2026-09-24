package com.chm.myfinances.domain.transfer;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Optional filter dimensions for {@link TransferRepository#findAll} (F005 spec's {@code GET
 * /api/transfers?dateFrom=&dateTo=&accountId=}). Every field is nullable - a {@code null} field
 * means "no constraint on this dimension". {@code accountId} matches either side of the transfer
 * (PRD S6.9's export filter semantics mirror this) - it isn't split into separate from/to filters.
 * {@code investmentProductId} (F009) narrows to one product's buys/sells.
 */
public record TransferFilter(
    LocalDate dateFrom, LocalDate dateTo, UUID accountId, UUID investmentProductId) {

  /** No filtering at all - every transfer matches. */
  public static TransferFilter none() {
    return new TransferFilter(null, null, null, null);
  }
}
