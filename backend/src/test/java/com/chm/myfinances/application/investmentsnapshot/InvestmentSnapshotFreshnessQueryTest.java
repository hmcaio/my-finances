package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InvestmentSnapshotFreshnessQuery} (F009 spec, rekeyed by holding for F022/ADR
 * 0020; reworked onto {@code TransferTradeLine}s by F027/ADR 0024): a holding {@code needsSnapshot}
 * as of a date when it has a line (tagged with its product *and* its account) dated after its
 * latest snapshot on or before that date, or a line and no snapshot. Computed on read, never
 * stored.
 */
class InvestmentSnapshotFreshnessQueryTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final InvestmentSnapshotFreshnessQuery query =
      new InvestmentSnapshotFreshnessQuery(
          new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
          tradeLineRepository,
          holdingRepository);

  private static final LocalDate ASOF = LocalDate.of(2026, 6, 30);
  private static final UUID CHECKING_ID = UUID.randomUUID();

  private final InvestmentHolding holding =
      holdingRepository.save(InvestmentHoldingMother.holding().build());
  private final InvestmentHolding otherHolding =
      holdingRepository.save(InvestmentHoldingMother.holding().build());

  /** A buy (into the holding's account) tagged with the holding's product, on {@code date}. */
  private void trade(InvestmentHolding holding, LocalDate date) {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            date,
            CHECKING_ID,
            holding.getAccountId(),
            holding.getProductId(),
            TradeSide.BUY,
            BigDecimal.ONE,
            BigDecimal.ONE,
            null));
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
  void holdingsAreIndependent() {
    trade(holding, LocalDate.of(2026, 4, 10));
    snapshot(otherHolding, LocalDate.of(2026, 5, 31));

    assertThat(query.staleHoldingIds(ASOF)).containsExactly(holding.getId());
  }
}
