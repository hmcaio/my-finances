package com.chm.myfinances.infrastructure.web.ledger;

import com.chm.myfinances.application.ledger.LedgerEntry;
import com.chm.myfinances.application.ledger.LedgerEntry.TransactionEntry;
import com.chm.myfinances.application.ledger.LedgerEntry.TransferEntry;
import com.chm.myfinances.application.ledger.LedgerEntryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.TradeConfirmation;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One Ledger row (F037), flat with {@code null} where a field does not apply: a transaction carries
 * {@code categoryId}/{@code accountId}/{@code paymentMethodId}, a transfer carries {@code
 * fromAccountId}/{@code toAccountId} and, for a trade confirmation, {@code tradeLines}. {@code id}
 * is the transaction's or transfer's own id - the client uses {@code type} to pick the endpoint for
 * edit and delete. {@code amount} is always positive.
 */
public record LedgerEntryResponse(
    UUID id,
    LedgerEntryType type,
    LocalDate date,
    BigDecimal amount,
    String description,
    String additionalNotes,
    UUID categoryId,
    UUID accountId,
    UUID paymentMethodId,
    UUID fromAccountId,
    UUID toAccountId,
    List<LedgerTradeLineResponse> tradeLines) {

  public static LedgerEntryResponse from(LedgerEntry entry) {
    return switch (entry) {
      case TransactionEntry transactionEntry -> fromTransaction(transactionEntry.transaction());
      case TransferEntry transferEntry -> fromTransfer(transferEntry.transfer());
    };
  }

  private static LedgerEntryResponse fromTransaction(Transaction transaction) {
    return new LedgerEntryResponse(
        transaction.getId(),
        new TransactionEntry(transaction).type(),
        transaction.getDate(),
        transaction.getAmount(),
        transaction.getDescription(),
        transaction.getAdditionalNotes(),
        transaction.getCategoryId(),
        transaction.getAccountId(),
        transaction.getPaymentMethodId(),
        null,
        null,
        List.of());
  }

  private static LedgerEntryResponse fromTransfer(Transfer transfer) {
    List<LedgerTradeLineResponse> tradeLines =
        transfer
            .getTradeConfirmation()
            .map(TradeConfirmation::getLines)
            .map(lines -> lines.stream().map(LedgerEntryResponse::toTradeLine).toList())
            .orElse(List.of());
    return new LedgerEntryResponse(
        transfer.getId(),
        LedgerEntryType.TRANSFER,
        transfer.getDate(),
        transfer.getAmount(),
        transfer.getDescription(),
        transfer.getAdditionalNotes(),
        null,
        null,
        null,
        transfer.getFromAccountId(),
        transfer.getToAccountId(),
        tradeLines);
  }

  private static LedgerTradeLineResponse toTradeLine(TradeConfirmationLine line) {
    return new LedgerTradeLineResponse(line.side(), line.productId());
  }
}
