package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AccountBalanceQuery}'s current opening-balance-only behavior (F003 plan.md).
 * F004 (Transaction)/F005 (Transfer) will add sum-of-activity cases here once they exist.
 */
class AccountBalanceQueryTest {

  private final AccountBalanceQuery query = new AccountBalanceQuery();

  @Test
  void balanceAsOfReturnsOpeningBalanceWhenNoActivityExists() {
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Checking",
            null,
            AccountType.CHECKING,
            new BigDecimal("321.45"),
            LocalDate.of(2026, 1, 1));

    BigDecimal balance = query.balanceAsOf(account, LocalDate.now());

    assertThat(balance).isEqualByComparingTo("321.45");
  }

  @Test
  void balanceAsOfIsIndependentOfAsOfDateForNow() {
    // No transactions/transfers exist yet, so any asOfDate - past, present, or before the
    // account's own opening date - must trivially return the opening balance.
    Account account =
        Account.create(
            UUID.randomUUID(),
            "Savings",
            null,
            AccountType.SAVINGS,
            new BigDecimal("1000.00"),
            LocalDate.of(2026, 6, 1));

    assertThat(query.balanceAsOf(account, LocalDate.of(2020, 1, 1)))
        .isEqualByComparingTo("1000.00");
    assertThat(query.balanceAsOf(account, LocalDate.now())).isEqualByComparingTo("1000.00");
  }
}
