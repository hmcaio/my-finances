package com.chm.myfinances.infrastructure.web.category;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/categories/{id}}. Name only — {@code type} is immutable after
 * creation (F002 spec) and deliberately isn't a field on this DTO at all, so there's no way to even
 * express a type change through this endpoint.
 */
public record UpdateCategoryRequest(@NotBlank String name) {}
