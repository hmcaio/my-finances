package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request body for {@code POST /api/categories}. */
public record CreateCategoryRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @NotNull CategoryType type) {}
