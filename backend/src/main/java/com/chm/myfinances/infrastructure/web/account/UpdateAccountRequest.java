package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.shared.NameConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/accounts/{id}}. Name/institution only - {@code type} and
 * opening balance/date are immutable after creation (F003 spec) and deliberately aren't fields on
 * this DTO at all, so there's no way to even express changing them through this endpoint.
 */
public record UpdateAccountRequest(
    @NotBlank @Size(max = NameConstraints.MAX_NAME_LENGTH) String name,
    @Size(max = NameConstraints.MAX_NAME_LENGTH) String institution) {}
