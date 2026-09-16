package com.chm.myfinances.infrastructure.web.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/transactions}. {@code type} is derived server-side from the
 * target category's own type (F004 spec) - never accepted as input here.
 */
public record CreateTransactionRequest(
    @NotNull LocalDate date,
    @NotNull @Positive BigDecimal amount,
    @NotNull UUID categoryId,
    @NotNull UUID accountId,
    @NotNull UUID paymentMethodId,
    String note) {}
