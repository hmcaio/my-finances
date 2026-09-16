package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of a {@link Transaction} (F004 spec). */
public record TransactionResponse(
    UUID id,
    LocalDate date,
    BigDecimal amount,
    UUID categoryId,
    CategoryType type,
    UUID accountId,
    UUID paymentMethodId,
    UUID recurringTemplateVersionId,
    String note) {

  public static TransactionResponse from(Transaction transaction) {
    return new TransactionResponse(
        transaction.getId(),
        transaction.getDate(),
        transaction.getAmount(),
        transaction.getCategoryId(),
        transaction.getType(),
        transaction.getAccountId(),
        transaction.getPaymentMethodId(),
        transaction.getRecurringTemplateVersionId(),
        transaction.getNote());
  }
}
