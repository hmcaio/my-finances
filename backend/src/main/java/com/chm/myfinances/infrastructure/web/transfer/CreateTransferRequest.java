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
 * Request body for {@code POST /api/transfers} (F005 spec). {@code fromAccountId != toAccountId}
 * can't be expressed as a per-field bean validation constraint, so it's checked at the application
 * layer ({@code TransferService}) instead, mapped to a 400 via {@code
 * SameAccountTransferException}.
 *
 * <p>F027 (ADR 0024, superseding F009's single-product shape) makes a transfer able to carry a
 * {@code tradeConfirmation}: {@code cashAccountId}/{@code investmentAccountId} (both unlabeled -
 * direction is derived) plus {@code tradeConfirmation}'s taxes/lines. Exactly one of {@code
 * {fromAccountId, toAccountId, amount}} (the plain shape) or {@code {cashAccountId,
 * investmentAccountId, tradeConfirmation}} (the confirmation shape) must be given - the client
 * never supplies {@code amount}/{@code fromAccountId}/{@code toAccountId} for a confirmation.
 * Checked by {@code TransferService.requireExactlyOneRequestShape} (400 {@code
 * InvalidTradeConfirmationException}), since it's a cross-field rule no single-field bean
 * validation annotation can express.
 */
public record CreateTransferRequest(
    @NotNull LocalDate date,
    UUID fromAccountId,
    UUID toAccountId,
    @Positive BigDecimal amount,
    @NotBlank @Size(max = TextFieldConstraints.MAX_DESCRIPTION_LENGTH) String description,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes,
    UUID cashAccountId,
    UUID investmentAccountId,
    @Valid TradeConfirmationRequest tradeConfirmation) {}
