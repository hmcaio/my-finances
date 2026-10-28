package com.chm.myfinances.application.networth;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.account.AccountLedgerActivityPort;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeSet;
import java.util.UUID;
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
  private final AccountLedgerActivityPort ledgerActivityPort;
  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentSnapshotRepository snapshotRepository;
  private final Clock clock;

  public NetWorthQuery(
      AccountRepository accountRepository,
      AccountBalanceQuery balanceQuery,
      AccountLedgerActivityPort ledgerActivityPort,
      TransactionRepository transactionRepository,
      TransferRepository transferRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentSnapshotRepository snapshotRepository,
      Clock clock) {
    this.accountRepository = accountRepository;
    this.balanceQuery = balanceQuery;
    this.ledgerActivityPort = ledgerActivityPort;
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
    this.holdingRepository = holdingRepository;
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
    List<InvestmentSnapshot> snapshots = snapshotRepository.findAll();
    List<LocalDate> dates = sampleDates(accounts, snapshots, from, last, today, granularity);
    return List.copyOf(trendPoints(accounts, snapshots, dates));
  }

  private List<LocalDate> sampleDates(
      List<Account> accounts,
      List<InvestmentSnapshot> snapshots,
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
    for (InvestmentSnapshot snapshot : snapshots) {
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
    List<Account> counted = accounts.stream().filter(a -> countsOn(a, date)).toList();
    Map<UUID, BigDecimal> balances = balanceQuery.balancesAsOf(counted, date);
    for (Account account : counted) {
      BigDecimal balance = balances.get(account.getId());
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
   * {@link #trend}'s computation: one forward pass over the sampled dates. The ledger accounts' net
   * effect per day comes from one grouped query ({@link
   * AccountLedgerActivityPort#dailyActivityUpTo}) and each account's running balance advances a
   * cursor over it; the {@code INVESTMENT} accounts' holdings and snapshots are read once and
   * walked the same way, so the statement count does not depend on the number of dates or rows
   * (issues #94, #189).
   */
  private List<NetWorthPoint> trendPoints(
      List<Account> accounts, List<InvestmentSnapshot> snapshots, List<LocalDate> dates) {
    if (dates.isEmpty()) {
      return List.of();
    }
    List<Account> ledgerAccounts =
        accounts.stream().filter(a -> a.getType() != AccountType.INVESTMENT).toList();
    Map<UUID, NavigableMap<LocalDate, BigDecimal>> daily =
        ledgerActivityPort.dailyActivityUpTo(ledgerAccounts, dates.get(dates.size() - 1));
    Map<UUID, LedgerWalk> ledgerWalks = new HashMap<>();
    for (Account account : ledgerAccounts) {
      ledgerWalks.put(
          account.getId(), new LedgerWalk(account.getOpeningBalance(), daily.get(account.getId())));
    }
    InvestmentWalk investmentWalk = new InvestmentWalk(snapshots, holdingRepository.findAll());

    List<NetWorthPoint> points = new ArrayList<>();
    for (LocalDate date : dates) {
      investmentWalk.advanceTo(date);
      BigDecimal assets = BigDecimal.ZERO.setScale(2);
      BigDecimal liabilities = BigDecimal.ZERO.setScale(2);
      BigDecimal investments = BigDecimal.ZERO.setScale(2);
      for (Account account : accounts) {
        if (!countsOn(account, date)) {
          continue;
        }
        BigDecimal balance =
            account.getType() == AccountType.INVESTMENT
                ? investmentWalk.balanceOf(account.getId())
                : ledgerWalks.get(account.getId()).balanceOn(date);
        switch (account.getType()) {
          case CHECKING, SAVINGS, CASH_WALLET -> assets = assets.add(balance);
          case INVESTMENT -> investments = investments.add(balance);
          case CREDIT_CARD -> liabilities = liabilities.add(balance);
        }
      }
      points.add(
          new NetWorthPoint(
              date,
              assets.add(investments).subtract(liabilities),
              assets,
              liabilities,
              investments));
    }
    return points;
  }

  /**
   * A ledger account's running balance over ascending dates: the opening balance plus the daily
   * activity on or before the date asked about (the sign rules live in the port, shared with {@link
   * AccountBalanceQuery}).
   */
  private static final class LedgerWalk {
    private final Iterator<Map.Entry<LocalDate, BigDecimal>> days;
    private Map.Entry<LocalDate, BigDecimal> next;
    private BigDecimal running;

    LedgerWalk(BigDecimal openingBalance, NavigableMap<LocalDate, BigDecimal> dailyActivity) {
      this.days = dailyActivity.entrySet().iterator();
      this.next = days.hasNext() ? days.next() : null;
      this.running = openingBalance;
    }

    BigDecimal balanceOn(LocalDate date) {
      while (next != null && !next.getKey().isAfter(date)) {
        running = running.add(next.getValue());
        next = days.hasNext() ? days.next() : null;
      }
      return running;
    }
  }

  /**
   * The {@code INVESTMENT} accounts' balances over ascending dates, the same rule as {@link
   * AccountBalanceQuery#balanceAsOf}: the sum of the latest snapshot on or before the date of each
   * of the account's holdings, a holding with none counting nothing.
   */
  private static final class InvestmentWalk {
    private final Map<UUID, List<UUID>> holdingIdsByAccount = new HashMap<>();
    private final Map<UUID, Iterator<InvestmentSnapshot>> pending = new HashMap<>();
    private final Map<UUID, InvestmentSnapshot> peeked = new HashMap<>();
    private final Map<UUID, BigDecimal> latestBalance = new HashMap<>();

    InvestmentWalk(List<InvestmentSnapshot> snapshots, List<InvestmentHolding> holdings) {
      for (InvestmentHolding holding : holdings) {
        holdingIdsByAccount
            .computeIfAbsent(holding.getAccountId(), id -> new ArrayList<>())
            .add(holding.getId());
      }
      Map<UUID, List<InvestmentSnapshot>> byHolding = new HashMap<>();
      for (InvestmentSnapshot snapshot : snapshots) {
        byHolding.computeIfAbsent(snapshot.getHoldingId(), id -> new ArrayList<>()).add(snapshot);
      }
      byHolding.forEach(
          (holdingId, list) -> {
            list.sort(Comparator.comparing(InvestmentSnapshot::getDate));
            Iterator<InvestmentSnapshot> iterator = list.iterator();
            pending.put(holdingId, iterator);
            peeked.put(holdingId, iterator.next());
          });
    }

    void advanceTo(LocalDate date) {
      for (Map.Entry<UUID, Iterator<InvestmentSnapshot>> entry : pending.entrySet()) {
        UUID holdingId = entry.getKey();
        InvestmentSnapshot candidate = peeked.get(holdingId);
        while (candidate != null && !candidate.getDate().isAfter(date)) {
          latestBalance.put(holdingId, candidate.getBalance());
          candidate = entry.getValue().hasNext() ? entry.getValue().next() : null;
        }
        peeked.put(holdingId, candidate);
      }
    }

    BigDecimal balanceOf(UUID accountId) {
      BigDecimal balance = BigDecimal.ZERO.setScale(2);
      for (UUID holdingId : holdingIdsByAccount.getOrDefault(accountId, List.of())) {
        BigDecimal latest = latestBalance.get(holdingId);
        if (latest != null) {
          balance = balance.add(latest);
        }
      }
      return balance;
    }
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
