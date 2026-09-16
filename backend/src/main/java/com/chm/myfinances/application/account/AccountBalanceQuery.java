package com.chm.myfinances.application.account;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Computes an account's running balance as of a given date (F003 spec, extended by F004): {@code
 * openingBalance} plus every transaction posted to the account up to {@code asOfDate}, each signed
 * by {@link #signedContribution} (PRD S5.3, S5.4).
 *
 * <p>Transfers (F005) aren't wired in yet - once that feature lands, its transfers-in/out sums join
 * the same formula here.
 */
@Service
public class AccountBalanceQuery {

  private final TransactionRepository transactionRepository;

  public AccountBalanceQuery(TransactionRepository transactionRepository) {
    this.transactionRepository = transactionRepository;
  }

  public BigDecimal balanceAsOf(Account account, LocalDate asOfDate) {
    BigDecimal balance = account.getOpeningBalance();
    for (Transaction transaction :
        transactionRepository.findByAccountIdOnOrBefore(account.getId(), asOfDate)) {
      balance = balance.add(signedContribution(transaction, account.getType()));
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
}
