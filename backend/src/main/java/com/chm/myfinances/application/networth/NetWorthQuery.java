package com.chm.myfinances.application.networth;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Net worth (F010 spec, PRD S5.9), computed on read from the accounts' balances and never stored:
 * {@code assets + investments - liabilities}, every balance as of the date. Asset accounts are
 * {@code CHECKING}/{@code SAVINGS}/{@code CASH_WALLET}, investments are {@code INVESTMENT} accounts
 * (their balance is the sum of their products' latest snapshots, already handled by {@link
 * AccountBalanceQuery}) and liabilities are {@code CREDIT_CARD} accounts.
 *
 * <p>Which accounts count on a date is as-of-aware ({@link #countsOn}): opened on or before it and
 * either never closed or closed after it. Summing "every non-closed account" instead would drop an
 * account from every past date once it is closed and corrupt the whole historical series.
 */
@Service
public class NetWorthQuery {

  private final AccountRepository accountRepository;
  private final AccountBalanceQuery balanceQuery;

  public NetWorthQuery(AccountRepository accountRepository, AccountBalanceQuery balanceQuery) {
    this.accountRepository = accountRepository;
    this.balanceQuery = balanceQuery;
  }

  /** Net worth and its three parts on {@code date}. */
  public NetWorthPoint asOf(LocalDate date) {
    return pointFor(accountRepository.findAll(), date);
  }

  private NetWorthPoint pointFor(List<Account> accounts, LocalDate date) {
    BigDecimal assets = BigDecimal.ZERO.setScale(2);
    BigDecimal liabilities = BigDecimal.ZERO.setScale(2);
    BigDecimal investments = BigDecimal.ZERO.setScale(2);
    for (Account account : accounts) {
      if (!countsOn(account, date)) {
        continue;
      }
      BigDecimal balance = balanceQuery.balanceAsOf(account, date);
      switch (account.getType()) {
        case CHECKING, SAVINGS, CASH_WALLET -> assets = assets.add(balance);
        case INVESTMENT -> investments = investments.add(balance);
        case CREDIT_CARD -> liabilities = liabilities.add(balance);
      }
    }
    return new NetWorthPoint(
        date, assets.add(investments).subtract(liabilities), assets, liabilities, investments);
  }

  /**
   * An account counts from its opening balance date (an {@code INVESTMENT} account has none - it
   * contributes {@code 0} until its first snapshot anyway) until its closed date, exclusive: closed
   * accounts still count in every earlier date (PRD S5.4).
   */
  static boolean countsOn(Account account, LocalDate date) {
    boolean opened =
        account.getType() == AccountType.INVESTMENT
            || !account.getOpeningBalanceDate().isAfter(date);
    boolean notYetClosed = account.getClosedDate() == null || account.getClosedDate().isAfter(date);
    return opened && notYetClosed;
  }
}
