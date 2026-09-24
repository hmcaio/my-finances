package com.chm.myfinances.application.account;

import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Computes an account's running balance as of a given date (F003 spec, extended by F004 and F005):
 * {@code openingBalance} plus every transaction and transfer posted to the account up to {@code
 * asOfDate}, each signed by {@link #signedContribution}/{@link #transferContribution} (PRD S5.3,
 * S5.4, S5.5). An {@code INVESTMENT} account is the exception (F009, ADR 0012): the sum of its
 * products' latest snapshots.
 */
@Service
public class AccountBalanceQuery {

  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;
  private final InvestmentProductRepository productRepository;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;

  public AccountBalanceQuery(
      TransactionRepository transactionRepository,
      TransferRepository transferRepository,
      InvestmentProductRepository productRepository,
      LatestInvestmentSnapshotQuery latestSnapshotQuery) {
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
    this.productRepository = productRepository;
    this.latestSnapshotQuery = latestSnapshotQuery;
  }

  public BigDecimal balanceAsOf(Account account, LocalDate asOfDate) {
    if (account.getType() == AccountType.INVESTMENT) {
      return investmentBalanceAsOf(account, asOfDate);
    }
    BigDecimal balance = account.getOpeningBalance();
    for (Transaction transaction :
        transactionRepository.findByAccountIdOnOrBefore(account.getId(), asOfDate)) {
      balance = balance.add(signedContribution(transaction, account.getType()));
    }
    for (Transfer transfer :
        transferRepository.findByAccountIdOnOrBefore(account.getId(), asOfDate)) {
      balance = balance.add(transferContribution(transfer, account.getId(), account.getType()));
    }
    return balance;
  }

  /**
   * An {@code INVESTMENT} account has no opening balance and transactions/transfers on it don't
   * contribute (ADR 0012): its balance is the sum of its products' latest snapshots as of the date
   * (F009). A product with no snapshot by then contributes nothing.
   */
  private BigDecimal investmentBalanceAsOf(Account account, LocalDate asOfDate) {
    Map<UUID, InvestmentSnapshot> latest = latestSnapshotQuery.latestByProduct(asOfDate);
    BigDecimal balance = BigDecimal.ZERO.setScale(2);
    for (InvestmentProduct product : productRepository.findByAccountId(account.getId())) {
      InvestmentSnapshot snapshot = latest.get(product.getId());
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
   * amount owed on that card.").
   */
  private static BigDecimal signedContribution(Transaction transaction, AccountType accountType) {
    boolean isExpense = transaction.getType() == CategoryType.EXPENSE;
    boolean isCreditCard = accountType == AccountType.CREDIT_CARD;
    boolean increases = isCreditCard == isExpense;
    return increases ? transaction.getAmount() : transaction.getAmount().negate();
  }

  /**
   * The source account's balance always decreases by the transfer amount, regardless of its type.
   * The destination account's effect depends on its own type: an asset account's balance increases;
   * a credit card account's owed balance decreases (PRD S5.5 - "an asset down and a liability down
   * by the same amount nets to zero", e.g. paying a credit card statement from checking).
   */
  private static BigDecimal transferContribution(
      Transfer transfer, UUID accountId, AccountType accountType) {
    if (transfer.getFromAccountId().equals(accountId)) {
      return transfer.getAmount().negate();
    }
    boolean isCreditCard = accountType == AccountType.CREDIT_CARD;
    return isCreditCard ? transfer.getAmount().negate() : transfer.getAmount();
  }
}
