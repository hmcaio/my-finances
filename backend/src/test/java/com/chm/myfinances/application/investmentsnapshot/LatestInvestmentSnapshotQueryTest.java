package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LatestInvestmentSnapshotQuery} (F009 spec, rekeyed by holding for F022/ADR
 * 0020): "latest snapshot per holding as of a date", reused by the allocation view, {@code
 * AccountBalanceQuery} and F010. A holding with no snapshot on or before the date is absent from
 * the result. {@link LatestInvestmentSnapshotQuery#totalValueOfProduct} rolls a product's holdings
 * back up (F022: a product can be held at more than one account).
 */
class LatestInvestmentSnapshotQueryTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final LatestInvestmentSnapshotQuery query =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);

  private final UUID holdingA = UUID.randomUUID();
  private final UUID holdingB = UUID.randomUUID();

  private void snapshot(UUID holdingId, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), holdingId, date, new BigDecimal(balance)));
  }

  @Test
  void latestOfPicksTheMostRecentSnapshotOnOrBeforeTheDate() {
    snapshot(holdingA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(holdingA, LocalDate.of(2026, 2, 28), "150.00");
    snapshot(holdingA, LocalDate.of(2026, 3, 31), "170.00");

    assertThat(query.latestOf(holdingA, LocalDate.of(2026, 3, 15)))
        .hasValueSatisfying(s -> assertThat(s.getBalance()).isEqualByComparingTo("150.00"));
  }

  @Test
  void latestOfIncludesASnapshotOnTheDateItself() {
    snapshot(holdingA, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(holdingA, LocalDate.of(2026, 2, 28)))
        .hasValueSatisfying(s -> assertThat(s.getBalance()).isEqualByComparingTo("150.00"));
  }

  @Test
  void latestOfIsEmptyWhenEverySnapshotIsAfterTheDate() {
    snapshot(holdingA, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(holdingA, LocalDate.of(2026, 2, 27))).isEmpty();
  }

  @Test
  void latestOfIsEmptyForAHoldingWithNoSnapshots() {
    snapshot(holdingB, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(holdingA, LocalDate.of(2026, 12, 31))).isEmpty();
  }

  @Test
  void latestByHoldingReturnsTheLatestPerHoldingAndOmitsHoldingsWithNoneYet() {
    snapshot(holdingA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(holdingA, LocalDate.of(2026, 2, 28), "150.00");
    snapshot(holdingB, LocalDate.of(2026, 3, 31), "999.00");

    Map<UUID, InvestmentSnapshot> latest = query.latestByHolding(LocalDate.of(2026, 3, 1));

    assertThat(latest).containsOnlyKeys(holdingA);
    assertThat(latest.get(holdingA).getBalance()).isEqualByComparingTo("150.00");
  }

  @Test
  void latestByHoldingWithSeveralHoldings() {
    snapshot(holdingA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(holdingB, LocalDate.of(2026, 1, 15), "40.00");
    snapshot(holdingB, LocalDate.of(2026, 2, 15), "60.00");

    Map<UUID, InvestmentSnapshot> latest = query.latestByHolding(LocalDate.of(2026, 12, 31));

    assertThat(latest.get(holdingA).getBalance()).isEqualByComparingTo("100.00");
    assertThat(latest.get(holdingB).getBalance()).isEqualByComparingTo("60.00");
  }

  @Test
  void latestByHoldingIsEmptyWhenNoSnapshotsExistOnOrBeforeTheDate() {
    snapshot(holdingA, LocalDate.of(2026, 5, 1), "100.00");

    assertThat(query.latestByHolding(LocalDate.of(2026, 4, 30))).isEmpty();
  }

  @Test
  void totalValueOfProductSumsAllItsHoldings() {
    UUID productId = UUID.randomUUID();
    UUID holdingAtXp =
        holdingRepository
            .save(InvestmentHoldingMother.holding().withProductId(productId).build())
            .getId();
    UUID holdingAtNu =
        holdingRepository
            .save(InvestmentHoldingMother.holding().withProductId(productId).build())
            .getId();
    snapshot(holdingAtXp, LocalDate.of(2026, 1, 31), "600.00");
    snapshot(holdingAtNu, LocalDate.of(2026, 1, 31), "400.00");

    assertThat(query.totalValueOfProduct(productId, LocalDate.of(2026, 2, 1)))
        .isEqualByComparingTo("1000.00");
  }

  @Test
  void totalValueOfProductTreatsAMissingHoldingSnapshotAsZero() {
    UUID productId = UUID.randomUUID();
    UUID holdingWithSnapshot =
        holdingRepository
            .save(InvestmentHoldingMother.holding().withProductId(productId).build())
            .getId();
    holdingRepository.save(InvestmentHoldingMother.holding().withProductId(productId).build());
    snapshot(holdingWithSnapshot, LocalDate.of(2026, 1, 31), "600.00");

    assertThat(query.totalValueOfProduct(productId, LocalDate.of(2026, 2, 1)))
        .isEqualByComparingTo("600.00");
  }

  @Test
  void totalValueOfProductIsZeroWithNoHoldingsAtAll() {
    assertThat(query.totalValueOfProduct(UUID.randomUUID(), LocalDate.of(2026, 2, 1)))
        .isEqualByComparingTo("0");
  }
}
