package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/transfers/{id}}. Every editable field is required (a
 * full-replace body), not a partial patch - same convention as F002/F003/F004's own PATCH endpoints
 * (F005 spec: "No versioning, no editing after creation beyond a plain field update").
 *
 * <p>F027 (ADR 0024) gives this the same shape as {@link CreateTransferRequest}: either the plain
 * {@code {fromAccountId, toAccountId, amount}} group or the {@code {cashAccountId,
 * investmentAccountId, tradeConfirmation}} group, mutually exclusive, both full-replace (omitting
 * {@code tradeConfirmation} clears it, same as the old "omitting investmentProductId clears the
 * tag").
 */
public record UpdateTransferRequest(
    @NotNull LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    @Positive BigDecimal amount,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    UUID cashAccountId,
    UUID investmentAccountId,
    @Valid TradeConfirmationRequest tradeConfirmation) {}
