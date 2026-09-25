package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.account.AccountUsageChecker;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Test double for {@link AccountUsageChecker}: answers {@code false} for every account unless the
 * test marks one as used (standing in for transactions, transfers, templates and products).
 */
public final class FakeAccountUsageChecker implements AccountUsageChecker {

  private final Set<UUID> usedAccounts = new HashSet<>();

  public void markUsed(UUID accountId) {
    usedAccounts.add(accountId);
  }

  @Override
  public boolean isUsed(UUID accountId) {
    return usedAccounts.contains(accountId);
  }
}
