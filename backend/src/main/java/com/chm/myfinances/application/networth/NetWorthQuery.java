package com.chm.myfinances.application.networth;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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

  /** Upper bound on the requested range, so one request can't fan out over decades of dates. */
  static final int MAX_MONTHS = 120;

  private final AccountRepository accountRepository;
  private final AccountBalanceQuery balanceQuery;
  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;
  private final InvestmentSnapshotRepository snapshotRepository;
  private final Clock clock;

  public NetWorthQuery(
      AccountRepository accountRepository,
      AccountBalanceQuery balanceQuery,
      TransactionRepository transactionRepository,
      TransferRepository transferRepository,
      InvestmentSnapshotRepository snapshotRepository,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.balanceQuery = balanceQuery;
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
    this.snapshotRepository = snapshotRepository;
    this.clock = clock;
  }

  /** Net worth and its three parts on {@code date}. */
  public NetWorthPoint asOf(LocalDate date) {
    return pointFor(accountRepository.findAll(), date);
  }

  /**
   * The net worth series over {@code from}..{@code to}, clamped to today (nothing after the current
   * date is reported). {@link NetWorthGranularity#MONTH}: one point per calendar month overlapping
   * the range, dated month-end (the current month at today), so quiet months carry the last value
   * forward. {@link NetWorthGranularity#CHANGE_DATE}: one point on each distinct date within the
   * range where a transaction, transfer or snapshot is dated, or an account is opened or closed
   * (the as-of filter makes those change net worth too). Evaluated straight from the raw data on
   * every call - no cache (F010 spec).
   */
  public List<NetWorthPoint> trend(LocalDate from, LocalDate to, NetWorthGranularity granularity) {
    if (from.isAfter(to)) {
      throw new InvalidNetWorthRangeException("from must not be after to");
    }
    if (to.isAfter(from.plusMonths(MAX_MONTHS))) {
      throw new InvalidNetWorthRangeException("range must not exceed " + MAX_MONTHS + " months");
    }
    LocalDate today = LocalDate.now(clock);
    LocalDate last = to.isAfter(today) ? today : to;
    if (from.isAfter(last)) {
      return List.of();
    }
    List<Account> accounts = accountRepository.findAll();
    List<NetWorthPoint> points = new ArrayList<>();
    for (LocalDate date : sampleDates(accounts, from, last, today, granularity)) {
      points.add(pointFor(accounts, date));
    }
    return List.copyOf(points);
  }

  private List<LocalDate> sampleDates(
      List<Account> accounts,
      LocalDate from,
      LocalDate last,
      LocalDate today,
      NetWorthGranularity granularity) {
    List<LocalDate> dates = new ArrayList<>();
    if (granularity == NetWorthGranularity.MONTH) {
      for (YearMonth month = YearMonth.from(from);
          !month.isAfter(YearMonth.from(last));
          month = month.plusMonths(1)) {
        LocalDate monthEnd = month.atEndOfMonth();
        dates.add(monthEnd.isAfter(today) ? today : monthEnd);
      }
      return dates;
    }
    TreeSet<LocalDate> changeDates = new TreeSet<>();
    changeDates.addAll(transactionRepository.findDistinctDatesBetween(from, last));
    changeDates.addAll(transferRepository.findDistinctDatesBetween(from, last));
    for (InvestmentSnapshot snapshot : snapshotRepository.findAll()) {
      changeDates.add(snapshot.getDate());
    }
    for (Account account : accounts) {
      if (account.getOpeningBalanceDate() != null) {
        changeDates.add(account.getOpeningBalanceDate());
      }
      if (account.getClosedDate() != null) {
        changeDates.add(account.getClosedDate());
      }
    }
    dates.addAll(changeDates.subSet(from, true, last, true));
    return dates;
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
