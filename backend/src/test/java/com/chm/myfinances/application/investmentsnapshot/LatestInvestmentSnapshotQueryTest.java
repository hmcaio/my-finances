package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LatestInvestmentSnapshotQuery} (F009 spec): "latest snapshot per product as of a
 * date", reused by the allocation view, {@code AccountBalanceQuery} and F010. A product with no
 * snapshot on or before the date is absent from the result.
 */
class LatestInvestmentSnapshotQueryTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final LatestInvestmentSnapshotQuery query =
      new LatestInvestmentSnapshotQuery(snapshotRepository);

  private final UUID productA = UUID.randomUUID();
  private final UUID productB = UUID.randomUUID();

  private void snapshot(UUID productId, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(UUID.randomUUID(), productId, date, new BigDecimal(balance)));
  }

  @Test
  void latestOfPicksTheMostRecentSnapshotOnOrBeforeTheDate() {
    snapshot(productA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(productA, LocalDate.of(2026, 2, 28), "150.00");
    snapshot(productA, LocalDate.of(2026, 3, 31), "170.00");

    assertThat(query.latestOf(productA, LocalDate.of(2026, 3, 15)))
        .hasValueSatisfying(s -> assertThat(s.getBalance()).isEqualByComparingTo("150.00"));
  }

  @Test
  void latestOfIncludesASnapshotOnTheDateItself() {
    snapshot(productA, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(productA, LocalDate.of(2026, 2, 28)))
        .hasValueSatisfying(s -> assertThat(s.getBalance()).isEqualByComparingTo("150.00"));
  }

  @Test
  void latestOfIsEmptyWhenEverySnapshotIsAfterTheDate() {
    snapshot(productA, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(productA, LocalDate.of(2026, 2, 27))).isEmpty();
  }

  @Test
  void latestOfIsEmptyForAProductWithNoSnapshots() {
    snapshot(productB, LocalDate.of(2026, 2, 28), "150.00");

    assertThat(query.latestOf(productA, LocalDate.of(2026, 12, 31))).isEmpty();
  }

  @Test
  void latestByProductReturnsTheLatestPerProductAndOmitsProductsWithNoneYet() {
    snapshot(productA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(productA, LocalDate.of(2026, 2, 28), "150.00");
    snapshot(productB, LocalDate.of(2026, 3, 31), "999.00");

    Map<UUID, InvestmentSnapshot> latest = query.latestByProduct(LocalDate.of(2026, 3, 1));

    assertThat(latest).containsOnlyKeys(productA);
    assertThat(latest.get(productA).getBalance()).isEqualByComparingTo("150.00");
  }

  @Test
  void latestByProductWithSeveralProducts() {
    snapshot(productA, LocalDate.of(2026, 1, 31), "100.00");
    snapshot(productB, LocalDate.of(2026, 1, 15), "40.00");
    snapshot(productB, LocalDate.of(2026, 2, 15), "60.00");

    Map<UUID, InvestmentSnapshot> latest = query.latestByProduct(LocalDate.of(2026, 12, 31));

    assertThat(latest.get(productA).getBalance()).isEqualByComparingTo("100.00");
    assertThat(latest.get(productB).getBalance()).isEqualByComparingTo("60.00");
  }

  @Test
  void latestByProductIsEmptyWhenNoSnapshotsExistOnOrBeforeTheDate() {
    snapshot(productA, LocalDate.of(2026, 5, 1), "100.00");

    assertThat(query.latestByProduct(LocalDate.of(2026, 4, 30))).isEmpty();
  }
}
