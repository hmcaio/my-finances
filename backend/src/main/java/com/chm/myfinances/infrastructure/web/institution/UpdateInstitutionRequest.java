package com.chm.myfinances.infrastructure.web.institution;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/institutions/{id}} (full replace: the name is the only
 * editable field). Also valid for the built-in row, which can be renamed.
 */
public record UpdateInstitutionRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
