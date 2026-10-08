package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/investment-segments/{id}}. */
public record UpdateInvestmentSegmentRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
