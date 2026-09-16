package com.chm.myfinances.infrastructure.web.transaction;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {}
