package com.chm.myfinances.infrastructure.investmentholding;

import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The real {@link HasHoldingHistoryChecker} (F022 spec, superseding F009's {@code
 * RealHasInvestmentHistoryChecker}): a holding has history when a snapshot row exists for it, or a
 * transfer is tagged with its product *and* its account (a trade never stores a holding id
 * directly, F022 spec - it's tagged with the product, and the account side of the transfer
 * identifies which holding).
 */
@Component
public class RealHasHoldingHistoryChecker implements HasHoldingHistoryChecker {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferRepository transferRepository;
  private final InvestmentHoldingRepository holdingRepository;

  public RealHasHoldingHistoryChecker(
      InvestmentSnapshotRepository snapshotRepository,
      TransferRepository transferRepository,
      InvestmentHoldingRepository holdingRepository) {
    this.snapshotRepository = snapshotRepository;
    this.transferRepository = transferRepository;
    this.holdingRepository = holdingRepository;
  }

  @Override
  public boolean hasHistory(UUID investmentHoldingId) {
    if (snapshotRepository.existsByHoldingId(investmentHoldingId)) {
      return true;
    }
    InvestmentHolding holding =
        holdingRepository
            .findById(investmentHoldingId)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown holding: " + investmentHoldingId));
    return transferRepository.findByInvestmentProductId(holding.getProductId()).stream()
        .anyMatch(
            trade ->
                trade.getFromAccountId().equals(holding.getAccountId())
                    || trade.getToAccountId().equals(holding.getAccountId()));
  }
}
