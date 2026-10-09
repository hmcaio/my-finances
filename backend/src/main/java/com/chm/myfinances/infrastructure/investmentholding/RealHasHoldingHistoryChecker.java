package com.chm.myfinances.infrastructure.investmentholding;

import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.transfer.TransferTradeLineRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The real {@link HasHoldingHistoryChecker} (F022 spec, superseding F009's {@code
 * RealHasInvestmentHistoryChecker}): a holding has history when a snapshot row exists for it, a
 * {@code TransferTradeLine} is tagged with its product *and* its account (F027, ADR 0024 - a line
 * never stores a holding id directly, it's tagged with the product, and the account side of its
 * parent transfer identifies which holding), or - F026 - a dividend {@code Transaction} is tagged
 * with this holding directly. Without this last check, deleting a holding with dividend history
 * would surface as a raw {@code transactions.investment_holding_id} FK violation (a 500), not this
 * clean 409 - the same "a new table with an FK must be added to the checker" rule backend {@code
 * CLAUDE.md} documents for {@code AccountUsageChecker}.
 */
@Component
public class RealHasHoldingHistoryChecker implements HasHoldingHistoryChecker {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final TransactionRepository transactionRepository;
  private final InvestmentHoldingRepository holdingRepository;

  public RealHasHoldingHistoryChecker(
      InvestmentSnapshotRepository snapshotRepository,
      TransferTradeLineRepository tradeLineRepository,
      TransactionRepository transactionRepository,
      InvestmentHoldingRepository holdingRepository) {
    this.snapshotRepository = snapshotRepository;
    this.tradeLineRepository = tradeLineRepository;
    this.transactionRepository = transactionRepository;
    this.holdingRepository = holdingRepository;
  }

  @Override
  public boolean hasHistory(UUID investmentHoldingId) {
    if (snapshotRepository.existsByHoldingId(investmentHoldingId)) {
      return true;
    }
    if (transactionRepository.existsByInvestmentHoldingId(investmentHoldingId)) {
      return true;
    }
    InvestmentHolding holding =
        holdingRepository
            .findById(investmentHoldingId)
            .orElseThrow(
                () -> new IllegalArgumentException("Unknown holding: " + investmentHoldingId));
    return tradeLineRepository.existsByProductIdAndAccountId(
        holding.getProductId(), holding.getAccountId());
  }
}
