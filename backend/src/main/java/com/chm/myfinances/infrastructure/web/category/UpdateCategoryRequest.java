package com.chm.myfinances.infrastructure.web.category;

import com.chm.myfinances.domain.shared.NameConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/categories/{id}}. Name only — {@code type} is immutable after
 * creation (F002 spec) and deliberately isn't a field on this DTO at all, so there's no way to even
 * express a type change through this endpoint.
 */
public record UpdateCategoryRequest(
    @NotBlank @Size(max = NameConstraints.MAX_NAME_LENGTH) String name) {}
