package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/investment-products/{id}}: a full replace, so every editable
 * field is present (F022 dropped {@code accountId} - a product no longer belongs to a single
 * account; the sub-category and notes stay optional, {@code null} clears either).
 */
public record UpdateInvestmentProductRequest(
    @NotNull UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @Size(max = TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) String additionalNotes) {}
