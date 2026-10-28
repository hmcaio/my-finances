package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.application.account.AccountBalanceQuery;
import com.chm.myfinances.application.account.AccountLedgerActivityPort;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The balance computation as it was before issue #189: loads each account's transactions and
 * transfers through the repository ports and sums them in Java with {@link
 * AccountBalanceQuery#signedContribution}/{@link AccountBalanceQuery#transferContribution}. Serves
 * as the fake of {@link AccountLedgerActivityPort} in the fast tier (built over the {@code Fake*}
 * repositories) and as the reference the SQL adapter is compared with in {@code
 * AccountLedgerActivityParityTest} (built over the real ones).
 */
public final class FakeAccountLedgerActivity implements AccountLedgerActivityPort {

  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;

  public FakeAccountLedgerActivity(
      TransactionRepository transactionRepository, TransferRepository transferRepository) {
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
  }

  @Override
  public Map<UUID, BigDecimal> activityAsOf(Collection<Account> accounts, LocalDate asOfDate) {
    Map<UUID, BigDecimal> activity = new HashMap<>();
    for (Account account : accounts) {
      BigDecimal sum = BigDecimal.ZERO;
      for (Transaction transaction :
          transactionRepository.findByAccountIdOnOrBefore(account.getId(), asOfDate)) {
        sum = sum.add(AccountBalanceQuery.signedContribution(transaction, account.getType()));
      }
      for (Transfer transfer :
          transferRepository.findByAccountIdOnOrBefore(account.getId(), asOfDate)) {
        sum =
            sum.add(
                AccountBalanceQuery.transferContribution(
                    transfer, account.getId(), account.getType()));
      }
      activity.put(account.getId(), sum);
    }
    return activity;
  }
}
