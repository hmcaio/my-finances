package com.chm.myfinances.infrastructure.account;

import com.chm.myfinances.domain.account.AccountUsageChecker;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The real {@link AccountUsageChecker} (ADR 0017): an account is used when a transaction, a
 * transfer on either side, a recurring template (active or not) or an investment product (open or
 * closed) references it - exactly the tables whose foreign keys would otherwise reject the delete.
 */
@Component
public class RealAccountUsageChecker implements AccountUsageChecker {

  private final TransactionRepository transactionRepository;
  private final TransferRepository transferRepository;
  private final RecurringTemplateRepository recurringTemplateRepository;
  private final InvestmentProductRepository investmentProductRepository;

  public RealAccountUsageChecker(
      TransactionRepository transactionRepository,
      TransferRepository transferRepository,
      RecurringTemplateRepository recurringTemplateRepository,
      InvestmentProductRepository investmentProductRepository) {
    this.transactionRepository = transactionRepository;
    this.transferRepository = transferRepository;
    this.recurringTemplateRepository = recurringTemplateRepository;
    this.investmentProductRepository = investmentProductRepository;
  }

  @Override
  public boolean isUsed(UUID accountId) {
    return transactionRepository.existsByAccountId(accountId)
        || transferRepository.existsByAccountId(accountId)
        || recurringTemplateRepository.existsByAccountId(accountId)
        || investmentProductRepository.existsByAccountId(accountId);
  }
}
