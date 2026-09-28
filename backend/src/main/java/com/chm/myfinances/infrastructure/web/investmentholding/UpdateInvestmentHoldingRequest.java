package com.chm.myfinances.infrastructure.web.investmentholding;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/investment-holdings/{id}}: only {@code additionalNotes} is
 * editable (F022 spec) - {@code productId}/{@code accountId} are immutable post-creation.
 */
public record UpdateInvestmentHoldingRequest(
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {}
