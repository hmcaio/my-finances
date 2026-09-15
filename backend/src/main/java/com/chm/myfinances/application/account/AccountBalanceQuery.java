package com.chm.myfinances.application.account;

import com.chm.myfinances.domain.account.Account;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Computes an account's running balance as of a given date (F003 spec): {@code openingBalance} +
 * sum(income transactions) - sum(expense transactions) + sum(transfers in) - sum(transfers out),
 * all up to {@code asOfDate} (PRD S5.4).
 *
 * <p>F004 (Transaction) and F005 (Transfer) don't exist yet, so this trivially returns {@code
 * openingBalance} regardless of {@code asOfDate} for now - revisit both the tests and this
 * implementation once those features land, per F003 plan.md.
 */
@Service
public class AccountBalanceQuery {

  public BigDecimal balanceAsOf(Account account, LocalDate asOfDate) {
    return account.getOpeningBalance();
  }
}
