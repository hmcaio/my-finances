package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import com.chm.myfinances.testsupport.mothers.TransferMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentSnapshotFreshnessQuery} (F009 spec, rekeyed by holding for F022/ADR
 * 0020): a holding {@code needsSnapshot} as of a date when it has a tagged transfer (a transfer
 * whose product and account match this holding) dated after its latest snapshot on or before that
 * date, or a trade and no snapshot. Computed on read, never stored.
 */
class InvestmentSnapshotFreshnessQueryTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final InvestmentSnapshotFreshnessQuery query =
      new InvestmentSnapshotFreshnessQuery(
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          transferRepository,
          holdingRepository);

  private static final LocalDate ASOF = LocalDate.of(2026, 6, 30);

  private final InvestmentHolding holding =
      holdingRepository.save(InvestmentHoldingMother.holding().build());
  private final InvestmentHolding otherHolding =
      holdingRepository.save(InvestmentHoldingMother.holding().build());

  /** A buy (into the holding's account) tagged with the holding's product, on {@code date}. */
  private void trade(InvestmentHolding holding, LocalDate date) {
    transferRepository.save(
        TransferMother.transfer()
            .withDate(date)
            .withToAccountId(holding.getAccountId())
            .withInvestmentProductId(holding.getProductId())
            .build());
  }

  private void snapshot(InvestmentHolding holding, LocalDate date) {
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), holding.getId(), date, BigDecimal.TEN));
  }

  @Test
  void aHoldingWithNoTradesNeverNeedsASnapshot() {
    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isFalse();
    snapshot(holding, LocalDate.of(2026, 1, 31));
    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isFalse();
  }

  @Test
  void aTradeAfterTheLatestSnapshotNeedsOne() {
    snapshot(holding, LocalDate.of(2026, 3, 31));
    trade(holding, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isTrue();
  }

  @Test
  void aTradeWithNoSnapshotAtAllNeedsOne() {
    trade(holding, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isTrue();
  }

  @Test
  void aSnapshotOnTheTradeDateIsFresh() {
    trade(holding, LocalDate.of(2026, 4, 10));
    snapshot(holding, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isFalse();
  }

  @Test
  void aSnapshotAfterTheTradeIsFresh() {
    trade(holding, LocalDate.of(2026, 4, 10));
    snapshot(holding, LocalDate.of(2026, 5, 31));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isFalse();
  }

  @Test
  void onlyTheLatestTradeMattersAgainstTheLatestSnapshot() {
    snapshot(holding, LocalDate.of(2026, 4, 30));
    trade(holding, LocalDate.of(2026, 4, 10));
    trade(holding, LocalDate.of(2026, 5, 20));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isTrue();
  }

  @Test
  void tradesAfterTheAsOfDateAreIgnored() {
    snapshot(holding, LocalDate.of(2026, 3, 31));
    trade(holding, LocalDate.of(2026, 7, 10));

    assertThat(query.needsSnapshot(holding.getId(), ASOF)).isFalse();
    assertThat(query.needsSnapshot(holding.getId(), LocalDate.of(2026, 7, 10))).isTrue();
  }

  @Test
  void snapshotsAfterTheAsOfDateDoNotFreshenAnEarlierDate() {
    trade(holding, LocalDate.of(2026, 4, 10));
    snapshot(holding, LocalDate.of(2026, 5, 31));

    assertThat(query.needsSnapshot(holding.getId(), LocalDate.of(2026, 5, 1))).isTrue();
    assertThat(query.needsSnapshot(holding.getId(), LocalDate.of(2026, 5, 31))).isFalse();
  }

  @Test
  void holdingsAreIndependentAndPlainTransfersAreIgnored() {
    trade(holding, LocalDate.of(2026, 4, 10));
    snapshot(otherHolding, LocalDate.of(2026, 5, 31));
    transferRepository.save(TransferMother.transfer().withDate(LocalDate.of(2026, 6, 1)).build());

    assertThat(query.staleHoldingIds(ASOF)).containsExactly(holding.getId());
  }
}
