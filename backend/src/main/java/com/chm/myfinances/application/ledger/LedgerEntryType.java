package com.chm.myfinances.application.ledger;

/**
 * What a Ledger row is (F037): the API {@code type} filter and each entry's {@code type}. A
 * transaction is {@link #INCOME} or {@link #EXPENSE} (its own category type); a transfer is {@link
 * #TRANSFER}.
 */
public enum LedgerEntryType {
  INCOME,
  EXPENSE,
  TRANSFER
}
