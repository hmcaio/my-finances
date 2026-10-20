package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/accounts/{id}}. Name/institution only - {@code type} and
 * opening balance/date are immutable after creation (F003 spec) and deliberately aren't fields on
 * this DTO at all, so there's no way to even express changing them through this endpoint. {@code
 * institutionId} is required (F017); moving an account "to no institution" means sending the
 * built-in institution's id.
 */
public record UpdateAccountRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @NotNull UUID institutionId) {}
