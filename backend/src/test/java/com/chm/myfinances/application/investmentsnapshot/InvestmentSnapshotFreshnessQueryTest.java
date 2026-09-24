package com.chm.myfinances.application.investmentsnapshot;

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
 * Tests for {@link InvestmentSnapshotFreshnessQuery} (F009 spec): a product {@code needsSnapshot}
 * as of a date when it has a tagged transfer dated after its latest snapshot on or before that
 * date, or a trade and no snapshot. Computed on read, never stored.
 */
class InvestmentSnapshotFreshnessQueryTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final InvestmentSnapshotFreshnessQuery query =
      new InvestmentSnapshotFreshnessQuery(
          new LatestInvestmentSnapshotQuery(snapshotRepository), transferRepository);

  private final UUID product = UUID.randomUUID();
  private final UUID otherProduct = UUID.randomUUID();
  private static final LocalDate ASOF = LocalDate.of(2026, 6, 30);

  private void trade(UUID productId, LocalDate date) {
    transferRepository.save(
        TransferMother.transfer().withDate(date).withInvestmentProductId(productId).build());
  }

  private void snapshot(UUID productId, LocalDate date) {
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), productId, date, BigDecimal.TEN));
  }

  @Test
  void aProductWithNoTradesNeverNeedsASnapshot() {
    assertThat(query.needsSnapshot(product, ASOF)).isFalse();
    snapshot(product, LocalDate.of(2026, 1, 31));
    assertThat(query.needsSnapshot(product, ASOF)).isFalse();
  }

  @Test
  void aTradeAfterTheLatestSnapshotNeedsOne() {
    snapshot(product, LocalDate.of(2026, 3, 31));
    trade(product, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(product, ASOF)).isTrue();
  }

  @Test
  void aTradeWithNoSnapshotAtAllNeedsOne() {
    trade(product, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(product, ASOF)).isTrue();
  }

  @Test
  void aSnapshotOnTheTradeDateIsFresh() {
    trade(product, LocalDate.of(2026, 4, 10));
    snapshot(product, LocalDate.of(2026, 4, 10));

    assertThat(query.needsSnapshot(product, ASOF)).isFalse();
  }

  @Test
  void aSnapshotAfterTheTradeIsFresh() {
    trade(product, LocalDate.of(2026, 4, 10));
    snapshot(product, LocalDate.of(2026, 5, 31));

    assertThat(query.needsSnapshot(product, ASOF)).isFalse();
  }

  @Test
  void onlyTheLatestTradeMattersAgainstTheLatestSnapshot() {
    snapshot(product, LocalDate.of(2026, 4, 30));
    trade(product, LocalDate.of(2026, 4, 10));
    trade(product, LocalDate.of(2026, 5, 20));

    assertThat(query.needsSnapshot(product, ASOF)).isTrue();
  }

  @Test
  void tradesAfterTheAsOfDateAreIgnored() {
    snapshot(product, LocalDate.of(2026, 3, 31));
    trade(product, LocalDate.of(2026, 7, 10));

    assertThat(query.needsSnapshot(product, ASOF)).isFalse();
    assertThat(query.needsSnapshot(product, LocalDate.of(2026, 7, 10))).isTrue();
  }

  @Test
  void snapshotsAfterTheAsOfDateDoNotFreshenAnEarlierDate() {
    trade(product, LocalDate.of(2026, 4, 10));
    snapshot(product, LocalDate.of(2026, 5, 31));

    assertThat(query.needsSnapshot(product, LocalDate.of(2026, 5, 1))).isTrue();
    assertThat(query.needsSnapshot(product, LocalDate.of(2026, 5, 31))).isFalse();
  }

  @Test
  void productsAreIndependentAndPlainTransfersAreIgnored() {
    trade(product, LocalDate.of(2026, 4, 10));
    snapshot(otherProduct, LocalDate.of(2026, 5, 31));
    transferRepository.save(TransferMother.transfer().withDate(LocalDate.of(2026, 6, 1)).build());

    assertThat(query.staleProductIds(ASOF)).containsExactly(product);
  }
}
