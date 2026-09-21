package com.chm.myfinances.infrastructure.web.investmentcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/investment-categories}. */
public record CreateInvestmentCategoryRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
