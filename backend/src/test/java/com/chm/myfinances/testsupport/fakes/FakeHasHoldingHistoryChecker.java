package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Test double for {@link HasHoldingHistoryChecker}: answers {@code false} for every holding unless
 * the test marks one as having history (standing in for a snapshot or a tagged transfer, F022).
 */
public final class FakeHasHoldingHistoryChecker implements HasHoldingHistoryChecker {

  private final Set<UUID> holdingsWithHistory = new HashSet<>();

  public void markHasHistory(UUID investmentHoldingId) {
    holdingsWithHistory.add(investmentHoldingId);
  }

  @Override
  public boolean hasHistory(UUID investmentHoldingId) {
    return holdingsWithHistory.contains(investmentHoldingId);
  }
}
