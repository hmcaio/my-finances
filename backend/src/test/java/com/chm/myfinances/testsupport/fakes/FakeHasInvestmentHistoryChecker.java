package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentproduct.HasInvestmentHistoryChecker;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Test double for {@link HasInvestmentHistoryChecker}: answers {@code false} for every product
 * unless the test marks one as having history (standing in for F009's snapshots/trades).
 */
public final class FakeHasInvestmentHistoryChecker implements HasInvestmentHistoryChecker {

  private final Set<UUID> productsWithHistory = new HashSet<>();

  public void markHasHistory(UUID investmentProductId) {
    productsWithHistory.add(investmentProductId);
  }

  @Override
  public boolean hasHistory(UUID investmentProductId) {
    return productsWithHistory.contains(investmentProductId);
  }
}
