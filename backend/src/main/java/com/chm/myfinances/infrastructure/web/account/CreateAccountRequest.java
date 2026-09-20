package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request body for {@code POST /api/accounts}. {@code institutionId} is required (F017). */
public record CreateAccountRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @NotNull UUID institutionId,
    @NotNull AccountType type,
    @NotNull BigDecimal openingBalance,
    @NotNull LocalDate openingBalanceDate) {}
