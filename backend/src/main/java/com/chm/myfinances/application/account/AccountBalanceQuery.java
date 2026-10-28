package com.chm.myfinances.application.account;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transfer.Transfer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes an account's running balance as of a given date (F003 spec, extended by F004 and F005):
 * {@code openingBalance} plus every transaction and transfer posted to the account up to {@code
 * asOfDate}, each signed by {@link #signedContribution}/{@link #transferContribution} (PRD S5.3,
 * S5.4, S5.5). The ledger sum comes from {@link AccountLedgerActivityPort}, computed in SQL (issue
 * #189). An {@code INVESTMENT} account is the exception (F009, ADR 0012, rewired onto holdings by
 * F022/ADR 0020): the sum of the latest snapshots of the holdings pointing at this account.
 */
@Service
public class AccountBalanceQuery {

  private final AccountLedgerActivityPort ledgerActivityPort;
  private final InvestmentHoldingRepository holdingRepository;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;

  public AccountBalanceQuery(
      AccountLedgerActivityPort ledgerActivityPort,
      InvestmentHoldingRepository holdingRepository,
      LatestInvestmentSnapshotQuery latestSnapshotQuery) {
    this.ledgerActivityPort = ledgerActivityPort;
    this.holdingRepository = holdingRepository;
    this.latestSnapshotQuery = latestSnapshotQuery;
  }

  public BigDecimal balanceAsOf(Account account, LocalDate asOfDate) {
    return balancesAsOf(List.of(account), asOfDate).get(account.getId());
  }

  /**
   * The balance of every given account as of the date, keyed by account id. The ledger accounts
   * cost one read-model query between them and the {@code INVESTMENT} ones one snapshot lookup,
   * whatever the number of accounts and ledger rows (issue #189).
   */
  public Map<UUID, BigDecimal> balancesAsOf(Collection<Account> accounts, LocalDate asOfDate) {
    List<Account> ledgerAccounts =
        accounts.stream().filter(a -> a.getType() != AccountType.INVESTMENT).toList();
    Map<UUID, BigDecimal> activity =
        ledgerAccounts.isEmpty()
            ? Map.of()
            : ledgerActivityPort.activityAsOf(ledgerAccounts, asOfDate);
    Map<UUID, InvestmentSnapshot> latest =
        ledgerAccounts.size() == accounts.size()
            ? Map.of()
            : latestSnapshotQuery.latestByHolding(asOfDate);
    Map<UUID, BigDecimal> balances = new HashMap<>();
    for (Account account : accounts) {
      balances.put(
          account.getId(),
          account.getType() == AccountType.INVESTMENT
              ? investmentBalance(account, latest)
              : account.getOpeningBalance().add(activity.get(account.getId())));
    }
    return balances;
  }

  /**
   * An {@code INVESTMENT} account has no opening balance and transactions/transfers on it don't
   * contribute (ADR 0012): its balance is the sum of the latest snapshots of the holdings pointing
   * at it as of the date, {@code latest} being those snapshots by holding (F009, rewired onto
   * holdings by F022). A holding with no snapshot by then contributes nothing.
   */
  private BigDecimal investmentBalance(Account account, Map<UUID, InvestmentSnapshot> latest) {
    BigDecimal balance = BigDecimal.ZERO.setScale(2);
    for (InvestmentHolding holding : holdingRepository.findByAccountId(account.getId())) {
      InvestmentSnapshot snapshot = latest.get(holding.getId());
      if (snapshot != null) {
        balance = balance.add(snapshot.getBalance());
      }
    }
    return balance;
  }

  /**
   * An expense reduces an asset account's balance and increases a credit card account's owed
   * balance; income does the reverse (PRD S5.3: "An expense transaction on an asset account ...
   * reduces that account's balance. An expense transaction on a credit card account *increases* the
   * amount owed on that card."). The reference statement of the sign rule: {@code
   * AccountLedgerActivityAdapter} restates it in SQL, and {@code AccountLedgerActivityParityTest}
   * runs the two against each other through {@code FakeAccountLedgerActivity}. Change both
   * together.
   */
  public static BigDecimal signedContribution(Transaction transaction, AccountType accountType) {
    boolean isExpense = transaction.getType() == CategoryType.EXPENSE;
    boolean isCreditCard = accountType == AccountType.CREDIT_CARD;
    boolean increases = isCreditCard == isExpense;
    return increases ? transaction.getAmount() : transaction.getAmount().negate();
  }

  /**
   * The source account's balance always decreases by the transfer amount, regardless of its type.
   * The destination account's effect depends on its own type: an asset account's balance increases;
   * a credit card account's owed balance decreases (PRD S5.5 - "an asset down and a liability down
   * by the same amount nets to zero", e.g. paying a credit card statement from checking). Also
   * restated in SQL: see {@link #signedContribution}.
   */
  public static BigDecimal transferContribution(
      Transfer transfer, UUID accountId, AccountType accountType) {
    if (transfer.getFromAccountId().equals(accountId)) {
      return transfer.getAmount().negate();
    }
    boolean isCreditCard = accountType == AccountType.CREDIT_CARD;
    return isCreditCard ? transfer.getAmount().negate() : transfer.getAmount();
  }
}
