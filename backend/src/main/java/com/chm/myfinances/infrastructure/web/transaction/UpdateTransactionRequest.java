package com.chm.myfinances.infrastructure.web.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/transactions/{id}}. Every editable field is required (a
 * full-replace body), not a partial patch - same convention as F002's {@code
 * UpdateCategoryRequest}/F003's {@code UpdateAccountRequest}: F004 spec says every one of these
 * fields (amount/date/category/account/payment method/note) is plainly editable in place, so
 * there's no immutable-subset split like Account's type/openingBalance to carve out of the DTO.
 * {@code recurringTemplateVersionId} has no field here at all - it's set once at creation and never
 * touched by edit (PRD S5.7).
 */
public record UpdateTransactionRequest(
    @NotNull LocalDate date,
    @NotNull @Positive BigDecimal amount,
    @NotNull UUID categoryId,
    @NotNull UUID accountId,
    @NotNull UUID paymentMethodId,
    String note) {}
