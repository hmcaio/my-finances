package com.chm.myfinances.application.ledger;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.Transfer;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the Ledger (F037): either a {@link Transaction} or a {@link Transfer}, exposing just
 * what the merge needs to order them. The web layer pattern-matches on the two cases to build the
 * flat response.
 */
public sealed interface LedgerEntry {

  UUID id();

  LocalDate date();

  LedgerEntryType type();

  /** A transaction row: {@link LedgerEntryType#INCOME} or {@link LedgerEntryType#EXPENSE}. */
  record TransactionEntry(Transaction transaction) implements LedgerEntry {

    @Override
    public UUID id() {
      return transaction.getId();
    }

    @Override
    public LocalDate date() {
      return transaction.getDate();
    }

    @Override
    public LedgerEntryType type() {
      return transaction.getType() == CategoryType.INCOME
          ? LedgerEntryType.INCOME
          : LedgerEntryType.EXPENSE;
    }
  }

  /** A transfer row. */
  record TransferEntry(Transfer transfer) implements LedgerEntry {

    @Override
    public UUID id() {
      return transfer.getId();
    }

    @Override
    public LocalDate date() {
      return transfer.getDate();
    }

    @Override
    public LedgerEntryType type() {
      return LedgerEntryType.TRANSFER;
    }
  }
}
