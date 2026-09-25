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

  // ---- update / delete (issue #59) ----

  private InvestmentSnapshot recordSnapshot(LocalDate date, String balance) {
    return service.record(product.getId(), date, new BigDecimal(balance)).snapshot();
  }

  private InvestmentProduct closedProduct() {
    InvestmentProduct closed = InvestmentProductMother.product().build();
    closed.close(LocalDate.of(2026, 4, 30));
    return productRepository.save(closed);
  }

  @Test
  void updateChangesTheDateAndBalanceOfTheSameRow() {
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            product.getId(), snapshot.getId(), LocalDate.of(2026, 3, 30), new BigDecimal("90.00"));

    assertThat(updated.getId()).isEqualTo(snapshot.getId());
    assertThat(updated.getDate()).isEqualTo(LocalDate.of(2026, 3, 30));
    assertThat(updated.getBalance()).isEqualByComparingTo("90.00");
    assertThat(snapshotRepository.findByProductId(product.getId())).hasSize(1);
  }

  @Test
  void updateKeepingTheSameDateIsNotADateConflict() {
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            product.getId(), snapshot.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1.00"));

    assertThat(updated.getBalance()).isEqualByComparingTo("1.00");
  }

  @Test
  void updateOntoADateThatAlreadyHasASnapshotIsRejectedAndChangesNothing() {
    recordSnapshot(LocalDate.of(2026, 3, 30), "50.00");
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    assertThatThrownBy(
            () ->
                service.update(
                    product.getId(),
                    snapshot.getId(),
                    LocalDate.of(2026, 3, 30),
                    new BigDecimal("1.00")))
        .isInstanceOf(InvestmentSnapshotDateTakenException.class);

    InvestmentSnapshot stored = snapshotRepository.findById(snapshot.getId()).orElseThrow();
    assertThat(stored.getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(stored.getBalance()).isEqualByComparingTo("100.00");
  }

  @Test
  void updateAllowsTheSameDateAsASnapshotOfAnotherProduct() {
    InvestmentProduct other = productRepository.save(InvestmentProductMother.product().build());
    service.record(other.getId(), LocalDate.of(2026, 3, 30), BigDecimal.TEN);
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            product.getId(), snapshot.getId(), LocalDate.of(2026, 3, 30), BigDecimal.ONE);

    assertThat(updated.getDate()).isEqualTo(LocalDate.of(2026, 3, 30));
  }

  @Test
  void updateRejectsAnUnknownSnapshotOrOneOfAnotherProduct() {
    InvestmentProduct other = productRepository.save(InvestmentProductMother.product().build());
    InvestmentSnapshot ofOther =
        service.record(other.getId(), LocalDate.of(2026, 3, 31), BigDecimal.TEN).snapshot();

    assertThatThrownBy(
            () ->
                service.update(product.getId(), UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThatThrownBy(
            () -> service.update(product.getId(), ofOther.getId(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThat(snapshotRepository.findById(ofOther.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("10");
  }

  @Test
  void updateRejectsAnUnknownProduct() {
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void updateOfAClosedProductIsRejectedWhenItWouldLeaveANonZeroLatestSnapshot() {
    InvestmentProduct closed = closedProduct();
    service.record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"));
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    assertThatThrownBy(
            () ->
                service.update(
                    closed.getId(), zero.getId(), LocalDate.of(2026, 4, 30), new BigDecimal("5")))
        .isInstanceOf(InvestmentSnapshotClosedProductException.class);
    // moving the zero snapshot before the non-zero one makes the non-zero one the latest
    assertThatThrownBy(
            () ->
                service.update(
                    closed.getId(), zero.getId(), LocalDate.of(2026, 3, 1), BigDecimal.ZERO))
        .isInstanceOf(InvestmentSnapshotClosedProductException.class);
    InvestmentSnapshot stored = snapshotRepository.findById(zero.getId()).orElseThrow();
    assertThat(stored.getBalance()).isEqualByComparingTo("0");
    assertThat(stored.getDate()).isEqualTo(LocalDate.of(2026, 4, 30));
  }

  @Test
  void updateOfAClosedProductIsAllowedWhenTheLatestSnapshotStaysZero() {
    InvestmentProduct closed = closedProduct();
    InvestmentSnapshot old =
        service
            .record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"))
            .snapshot();
    service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO);

    InvestmentSnapshot updated =
        service.update(
            closed.getId(), old.getId(), LocalDate.of(2026, 3, 30), new BigDecimal("80.00"));

    assertThat(updated.getBalance()).isEqualByComparingTo("80.00");
  }

  @Test
  void deleteRemovesTheSnapshot() {
    InvestmentSnapshot keep = recordSnapshot(LocalDate.of(2026, 3, 30), "50.00");
    InvestmentSnapshot gone = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    service.delete(product.getId(), gone.getId());

    assertThat(snapshotRepository.findByProductId(product.getId()))
        .extracting(InvestmentSnapshot::getId)
        .containsExactly(keep.getId());
  }

  @Test
  void deleteRejectsAnUnknownSnapshotOrOneOfAnotherProduct() {
    InvestmentProduct other = productRepository.save(InvestmentProductMother.product().build());
    InvestmentSnapshot ofOther =
        service.record(other.getId(), LocalDate.of(2026, 3, 31), BigDecimal.TEN).snapshot();

    assertThatThrownBy(() -> service.delete(product.getId(), UUID.randomUUID()))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThatThrownBy(() -> service.delete(product.getId(), ofOther.getId()))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThat(snapshotRepository.findById(ofOther.getId())).isPresent();
  }

  @Test
  void deleteRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void deleteOfAClosedProductIsRejectedWhenTheRemainingLatestSnapshotIsNonZero() {
    InvestmentProduct closed = closedProduct();
    service.record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"));
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    assertThatThrownBy(() -> service.delete(closed.getId(), zero.getId()))
        .isInstanceOf(InvestmentSnapshotClosedProductException.class);
    assertThat(snapshotRepository.findById(zero.getId())).isPresent();
  }

  @Test
  void deleteOfAClosedProductIsAllowedWhenTheRemainingLatestIsZeroOrNone() {
    InvestmentProduct closed = closedProduct();
    InvestmentSnapshot old =
        service
            .record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"))
            .snapshot();
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    service.delete(closed.getId(), old.getId());
    service.delete(closed.getId(), zero.getId());

    assertThat(snapshotRepository.findByProductId(closed.getId())).isEmpty();
  }
}
