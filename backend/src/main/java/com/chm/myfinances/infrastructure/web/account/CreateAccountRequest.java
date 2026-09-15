package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.shared.NameConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Request body for {@code POST /api/accounts}. */
public record CreateAccountRequest(
    @NotBlank @Size(max = NameConstraints.MAX_NAME_LENGTH) String name,
    @Size(max = NameConstraints.MAX_NAME_LENGTH) String institution,
    @NotNull AccountType type,
    @NotNull BigDecimal openingBalance,
    @NotNull LocalDate openingBalanceDate) {}
