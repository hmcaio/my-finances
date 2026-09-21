package com.chm.myfinances.infrastructure.web.investmentsubcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/investment-subcategories/{id}}. Name only - the parent
 * category is immutable (re-parenting would silently reclassify every product beneath) and
 * deliberately is not a field on this DTO at all.
 */
public record UpdateInvestmentSubcategoryRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
