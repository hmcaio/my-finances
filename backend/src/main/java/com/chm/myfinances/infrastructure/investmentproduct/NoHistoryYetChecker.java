package com.chm.myfinances.infrastructure.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.HasInvestmentHistoryChecker;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Placeholder {@link HasInvestmentHistoryChecker} (F008): history means snapshots or buy/sell
 * transfers, both F009's tables, which don't exist yet - so no product has any history and every
 * product stays hard-deletable. F009 replaces this class with the real check (a snapshot row for
 * the product, or any transfer tagged with it), the same way F007 replaced F003's no-op {@code
 * AccountClosedNotifier}.
 */
@Component
public class NoHistoryYetChecker implements HasInvestmentHistoryChecker {

  @Override
  public boolean hasHistory(UUID investmentProductId) {
    return false;
  }
}
