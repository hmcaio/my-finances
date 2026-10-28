package com.chm.myfinances.application.report;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Report 2 (F030 spec): every account's balance as of a selectable date, falling back to the latest
 * value at or before it wherever {@link AccountBalanceQuery} itself is already as-of-aware (true of
 * every account type - no new fallback logic needed, per the spec's Decisions).
 *
 * <p>{@link com.chm.myfinances.application.report.ReportFilter} has no dedicated "include closed
 * accounts" dimension, so every account (open and closed) is always included, with its own {@code
 * closed_date} column signaling status - the simplest way to answer "and, if asked, closed" without
 * a new filter field the spec's {@code ReportFilter} shape doesn't carry.
 */
@Service
public class AccountBalanceReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS =
      List.of("account_name", "type", "balance", "closed_date");

  private final AccountRepository accountRepository;
  private final AccountBalanceQuery balanceQuery;
  private final Clock clock;

  public AccountBalanceReportGenerator(
      AccountRepository accountRepository, AccountBalanceQuery balanceQuery, Clock clock) {
    this.accountRepository = accountRepository;
    this.balanceQuery = balanceQuery;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.ACCOUNT_BALANCES;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    LocalDate asOf = filter.asOfDate() != null ? filter.asOfDate() : LocalDate.now(clock);

    List<Account> accounts =
        accountRepository.findAll().stream()
            .filter(a -> filter.accountId() == null || filter.accountId().equals(a.getId()))
            .sorted(Comparator.comparing(Account::getName))
            .toList();

    Map<UUID, BigDecimal> balances = balanceQuery.balancesAsOf(accounts, asOf);
    List<List<Object>> rows = new ArrayList<>();
    for (Account account : accounts) {
      rows.add(
          Arrays.asList(
              account.getName(),
              account.getType().name(),
              balances.get(account.getId()),
              account.getClosedDate()));
    }
    byte[] csv = CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, rows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
