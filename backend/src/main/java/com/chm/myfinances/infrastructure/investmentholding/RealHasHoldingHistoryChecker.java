package com.chm.myfinances.infrastructure.investmentholding;

import com.chm.myfinances.domain.investmentholding.HasHoldingHistoryChecker;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPositionRepository;
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
 * with this holding directly, or - F038, ADR 0033 - an opening position exists for it. Without the
 * last two checks, deleting such a holding would surface as a raw FK violation (a 500, from {@code
 * transactions.investment_holding_id} or {@code investment_opening_positions.holding_id}), not this
 * clean 409 - the same "a new table with an FK must be added to the checker" rule {@code
 * docs/conventions/accounts-and-investments.md} documents for {@code AccountUsageChecker}.
 */
@Component
public class RealHasHoldingHistoryChecker implements HasHoldingHistoryChecker {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferTradeLineRepository tradeLineRepository;
  private final TransactionRepository transactionRepository;
  private final InvestmentHoldingRepository holdingRepository;
  private final InvestmentOpeningPositionRepository openingPositionRepository;

  public RealHasHoldingHistoryChecker(
      InvestmentSnapshotRepository snapshotRepository,
      TransferTradeLineRepository tradeLineRepository,
      TransactionRepository transactionRepository,
      InvestmentHoldingRepository holdingRepository,
      InvestmentOpeningPositionRepository openingPositionRepository) {
    this.snapshotRepository = snapshotRepository;
    this.tradeLineRepository = tradeLineRepository;
    this.transactionRepository = transactionRepository;
    this.holdingRepository = holdingRepository;
    this.openingPositionRepository = openingPositionRepository;
  }

  @Override
  public boolean hasHistory(UUID investmentHoldingId) {
    if (snapshotRepository.existsByHoldingId(investmentHoldingId)) {
      return true;
    }
    if (transactionRepository.existsByInvestmentHoldingId(investmentHoldingId)) {
      return true;
    }
    if (openingPositionRepository.existsByHoldingId(investmentHoldingId)) {
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
