package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.domain.category.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Request body for {@code POST /api/categories}. */
public record CreateCategoryRequest(@NotBlank String name, @NotNull CategoryType type) {}
