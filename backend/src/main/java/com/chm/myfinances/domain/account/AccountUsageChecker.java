package com.chm.myfinances.domain.account;

import java.util.UUID;

/**
 * Port answering "does anything reference this account?" (ADR 0017). Implemented in {@code
 * infrastructure/account/RealAccountUsageChecker} over the transaction, transfer, recurring
 * template and investment product repositories, so {@code AccountService} can guard a delete
 * without depending on those aggregates (same shape as {@code HasInvestmentHistoryChecker}).
 */
public interface AccountUsageChecker {

  /**
   * Whether any transaction, transfer (either side), recurring template (active or not) or
   * investment product (open or closed) references the account.
   */
  boolean isUsed(UUID accountId);
}
