package com.chm.myfinances.infrastructure.web.investmentsubcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body for {@code POST /api/investment-subcategories}. */
public record CreateInvestmentSubcategoryRequest(
    @NotNull UUID investmentCategoryId,
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
