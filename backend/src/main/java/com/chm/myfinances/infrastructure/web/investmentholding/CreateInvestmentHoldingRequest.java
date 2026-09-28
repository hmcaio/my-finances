package com.chm.myfinances.infrastructure.web.investmentholding;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body for {@code POST /api/investment-holdings} (F022 spec). */
public record CreateInvestmentHoldingRequest(
    @NotNull UUID productId,
    @NotNull UUID accountId,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {}
