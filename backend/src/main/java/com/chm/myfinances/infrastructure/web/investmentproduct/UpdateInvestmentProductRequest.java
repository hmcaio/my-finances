package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/investment-products/{id}}: a full replace, so every editable
 * field is present (the sub-category stays optional - {@code null} clears it).
 */
public record UpdateInvestmentProductRequest(
    @NotNull UUID accountId,
    @NotNull UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
