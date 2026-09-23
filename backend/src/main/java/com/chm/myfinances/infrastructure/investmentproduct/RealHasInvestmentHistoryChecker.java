package com.chm.myfinances.infrastructure.investmentproduct;

import com.chm.myfinances.domain.investmentproduct.HasInvestmentHistoryChecker;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.transfer.TransferRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The real {@link HasInvestmentHistoryChecker} (F009 fulfilling F008's port, replacing its {@code
 * NoHistoryYetChecker} placeholder the same way F007 replaced F003's no-op {@code
 * AccountClosedNotifier}): a product has history when a snapshot row exists for it or any transfer
 * is tagged with it (PRD S5.8's delete-safety rule).
 */
@Component
public class RealHasInvestmentHistoryChecker implements HasInvestmentHistoryChecker {

  private final InvestmentSnapshotRepository snapshotRepository;
  private final TransferRepository transferRepository;

  public RealHasInvestmentHistoryChecker(
      InvestmentSnapshotRepository snapshotRepository, TransferRepository transferRepository) {
    this.snapshotRepository = snapshotRepository;
    this.transferRepository = transferRepository;
  }

  @Override
  public boolean hasHistory(UUID investmentProductId) {
    return snapshotRepository.existsByProductId(investmentProductId)
        || transferRepository.existsByInvestmentProductId(investmentProductId);
  }
}
