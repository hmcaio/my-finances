package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/transfers} (F005 spec). {@code fromAccountId != toAccountId}
 * can't be expressed as a per-field bean validation constraint, so it's checked at the application
 * layer ({@code TransferService}) instead, mapped to a 400 via {@code
 * SameAccountTransferException}.
 */
public record CreateTransferRequest(
    @NotNull LocalDate date,
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {}
