package com.chm.myfinances.infrastructure.web.account;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API representation of an {@link Account}, including its computed running {@code balance} (F003
 * spec). {@code balance} isn't a field on the domain object itself - the controller supplies it by
 * calling {@code AccountBalanceQuery} separately and passing the result in here.
 */
public record AccountResponse(
    UUID id,
    String name,
    UUID institutionId,
    AccountType type,
    BigDecimal openingBalance,
    LocalDate openingBalanceDate,
    LocalDate closedDate,
    boolean closed,
    BigDecimal balance) {

  public static AccountResponse from(Account account, BigDecimal balance) {
    return new AccountResponse(
        account.getId(),
        account.getName(),
        account.getInstitutionId(),
        account.getType(),
        account.getOpeningBalance(),
        account.getOpeningBalanceDate(),
        account.getClosedDate(),
        account.isClosed(),
        balance);
  }
}
