package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.shared.TextFieldConstraints;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for {@code POST /api/accounts}. {@code institutionId} is required (F017). {@code
 * openingBalance}/{@code openingBalanceDate} depend on the type (F008, ADR 0012): required for
 * every type except {@code INVESTMENT}, and absent for it (its value comes from snapshots). That
 * cross-field rule is a {@code 400} here, backed by the domain constructor and a DB CHECK.
 */
public record CreateAccountRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name,
    @NotNull UUID institutionId,
    @NotNull AccountType type,
    BigDecimal openingBalance,
    LocalDate openingBalanceDate) {

  /**
   * Whether the opening fields match the type. A missing type is left to its own {@code @NotNull}
   * (this returns {@code true} so it isn't reported twice).
   */
  @JsonIgnore
  @AssertTrue(
      message =
          "openingBalance and openingBalanceDate are required unless type is INVESTMENT, and must"
              + " be absent for it")
  public boolean isOpeningFieldsConsistentWithType() {
    if (type == null) {
      return true;
    }
    boolean bothAbsent = openingBalance == null && openingBalanceDate == null;
    boolean bothPresent = openingBalance != null && openingBalanceDate != null;
    return type == AccountType.INVESTMENT ? bothAbsent : bothPresent;
  }
}
