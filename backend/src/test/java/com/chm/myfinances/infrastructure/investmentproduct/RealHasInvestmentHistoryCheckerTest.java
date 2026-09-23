package com.chm.myfinances.infrastructure.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RealHasInvestmentHistoryChecker} (F009 fulfilling F008's port): a product has
 * history once a snapshot exists for it or any transfer is tagged with it - never because of
 * someone else's snapshot or trade.
 */
class RealHasInvestmentHistoryCheckerTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final RealHasInvestmentHistoryChecker checker =
      new RealHasInvestmentHistoryChecker(snapshotRepository, transferRepository);

  @Test
  void hasNoHistoryForABrandNewProduct() {
    assertThat(checker.hasHistory(UUID.randomUUID())).isFalse();
  }

  @Test
  void hasHistoryOnceASnapshotExists() {
    UUID productId = UUID.randomUUID();
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), productId, LocalDate.of(2026, 1, 31), BigDecimal.ZERO));

    assertThat(checker.hasHistory(productId)).isTrue();
  }

  @Test
  void hasHistoryOnceATaggedTransferExists() {
    UUID productId = UUID.randomUUID();
    transferRepository.save(TransferMother.transfer().withInvestmentProductId(productId).build());

    assertThat(checker.hasHistory(productId)).isTrue();
  }

  @Test
  void ignoresSnapshotsAndTransfersOfOtherProductsAndPlainTransfers() {
    UUID productId = UUID.randomUUID();
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), UUID.randomUUID(), LocalDate.of(2026, 1, 31), BigDecimal.TEN));
    transferRepository.save(
        TransferMother.transfer().withInvestmentProductId(UUID.randomUUID()).build());
    transferRepository.save(TransferMother.transfer().build());

    assertThat(checker.hasHistory(productId)).isFalse();
  }
}
