package com.chm.myfinances.infrastructure.web.investmentproduct;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code POST /api/investment-products}. The sub-category is optional (some
 * categories, like Crypto, have no natural sub-level).
 */
public record CreateInvestmentProductRequest(
    @NotNull UUID accountId,
    @NotNull UUID investmentCategoryId,
    UUID investmentSubcategoryId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
