package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentSnapshotService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context. Covers F009 spec's upsert on {@code
 * (productId, date)}: create, same-day replace, unknown product.
 */
class InvestmentSnapshotServiceTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final InvestmentSnapshotService service =
      new InvestmentSnapshotService(snapshotRepository, productRepository, idGenerator);

  private InvestmentProduct product;

  @BeforeEach
  void setUp() {
    product = productRepository.save(InvestmentProductMother.product().build());
  }

  @Test
  void recordCreatesASnapshotWithAnIdFromTheGenerator() {
    UUID nextId = UUID.randomUUID();
    InvestmentSnapshotService service =
        new InvestmentSnapshotService(
            snapshotRepository, productRepository, new FakeIdGenerator(nextId));

    RecordedSnapshot recorded =
        service.record(product.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1234.56"));

    assertThat(recorded.created()).isTrue();
    assertThat(recorded.snapshot().getId()).isEqualTo(nextId);
    assertThat(recorded.snapshot().getProductId()).isEqualTo(product.getId());
    assertThat(recorded.snapshot().getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(recorded.snapshot().getBalance()).isEqualByComparingTo("1234.56");
    assertThat(snapshotRepository.findById(nextId)).isPresent();
  }

  @Test
  void recordOnTheSameDayReplacesTheBalanceInsteadOfAddingARow() {
    LocalDate date = LocalDate.of(2026, 3, 31);
    RecordedSnapshot first = service.record(product.getId(), date, new BigDecimal("100.00"));

    RecordedSnapshot second = service.record(product.getId(), date, new BigDecimal("120.00"));

    assertThat(second.created()).isFalse();
    assertThat(second.snapshot().getId()).isEqualTo(first.snapshot().getId());
    assertThat(second.snapshot().getBalance()).isEqualByComparingTo("120.00");
    assertThat(snapshotRepository.findByProductId(product.getId())).hasSize(1);
    assertThat(snapshotRepository.findByProductId(product.getId()).get(0).getBalance())
        .isEqualByComparingTo("120.00");
  }

  @Test
  void recordOnADifferentDayAddsARow() {
    service.record(product.getId(), LocalDate.of(2026, 3, 30), new BigDecimal("100.00"));

    RecordedSnapshot second =
        service.record(product.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("110.00"));

    assertThat(second.created()).isTrue();
    assertThat(snapshotRepository.findByProductId(product.getId())).hasSize(2);
  }

  @Test
  void recordAllowsAZeroBalance() {
    RecordedSnapshot recorded =
        service.record(product.getId(), LocalDate.of(2026, 3, 31), BigDecimal.ZERO);

    assertThat(recorded.snapshot().getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void recordRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> service.record(UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN))
        .isInstanceOf(InvestmentProductNotFoundException.class);
    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void findByProductListsSnapshotsMostRecentFirst() {
    service.record(product.getId(), LocalDate.of(2026, 1, 31), new BigDecimal("100.00"));
    service.record(product.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("300.00"));
    service.record(product.getId(), LocalDate.of(2026, 2, 28), new BigDecimal("200.00"));

    List<InvestmentSnapshot> snapshots = service.findByProduct(product.getId());

    assertThat(snapshots)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactly(
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31));
  }

  @Test
  void findByProductRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> service.findByProduct(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }
}
