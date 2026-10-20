package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentSnapshotService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context. Covers F009 spec's upsert on {@code
 * (holdingId, date)} (rekeyed from {@code productId} by F022/ADR 0020): create, same-day replace,
 * unknown holding.
 */
class InvestmentSnapshotServiceTest {

  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final InvestmentSnapshotService service =
      new InvestmentSnapshotService(
          snapshotRepository,
          holdingRepository,
          idGenerator,
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

  private InvestmentHolding holding;

  @BeforeEach
  void setUp() {
    holding = holdingRepository.save(InvestmentHoldingMother.holding().build());
  }

  @Test
  void recordCreatesASnapshotWithAnIdFromTheGenerator() {
    UUID nextId = UUID.randomUUID();
    InvestmentSnapshotService service =
        new InvestmentSnapshotService(
            snapshotRepository,
            holdingRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog, AuditReferenceLabels.none()));

    RecordedSnapshot recorded =
        service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1234.56"));

    assertThat(recorded.created()).isTrue();
    assertThat(recorded.snapshot().getId()).isEqualTo(nextId);
    assertThat(recorded.snapshot().getHoldingId()).isEqualTo(holding.getId());
    assertThat(recorded.snapshot().getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(recorded.snapshot().getBalance()).isEqualByComparingTo("1234.56");
    assertThat(snapshotRepository.findById(nextId)).isPresent();
  }

  @Test
  void recordOnTheSameDayReplacesTheBalanceInsteadOfAddingARow() {
    LocalDate date = LocalDate.of(2026, 3, 31);
    RecordedSnapshot first = service.record(holding.getId(), date, new BigDecimal("100.00"));

    RecordedSnapshot second = service.record(holding.getId(), date, new BigDecimal("120.00"));

    assertThat(second.created()).isFalse();
    assertThat(second.snapshot().getId()).isEqualTo(first.snapshot().getId());
    assertThat(second.snapshot().getBalance()).isEqualByComparingTo("120.00");
    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(1);
    assertThat(snapshotRepository.findByHoldingId(holding.getId()).get(0).getBalance())
        .isEqualByComparingTo("120.00");
  }

  @Test
  void recordOnADifferentDayAddsARow() {
    service.record(holding.getId(), LocalDate.of(2026, 3, 30), new BigDecimal("100.00"));

    RecordedSnapshot second =
        service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("110.00"));

    assertThat(second.created()).isTrue();
    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(2);
  }

  @Test
  void recordAllowsAZeroBalance() {
    RecordedSnapshot recorded =
        service.record(holding.getId(), LocalDate.of(2026, 3, 31), BigDecimal.ZERO);

    assertThat(recorded.snapshot().getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void recordRejectsAnUnknownHolding() {
    assertThatThrownBy(() -> service.record(UUID.randomUUID(), LocalDate.now(), BigDecimal.TEN))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void findByHoldingListsSnapshotsMostRecentFirst() {
    service.record(holding.getId(), LocalDate.of(2026, 1, 31), new BigDecimal("100.00"));
    service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("300.00"));
    service.record(holding.getId(), LocalDate.of(2026, 2, 28), new BigDecimal("200.00"));

    List<InvestmentSnapshot> snapshots = service.findByHolding(holding.getId());

    assertThat(snapshots)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactly(
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31));
  }

  @Test
  void findByHoldingRejectsAnUnknownHolding() {
    assertThatThrownBy(() -> service.findByHolding(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  // ---- update / delete (issue #59) ----

  private InvestmentSnapshot recordSnapshot(LocalDate date, String balance) {
    return service.record(holding.getId(), date, new BigDecimal(balance)).snapshot();
  }

  private InvestmentHolding closedHolding() {
    InvestmentHolding closed = InvestmentHoldingMother.holding().build();
    closed.close(LocalDate.of(2026, 4, 30));
    return holdingRepository.save(closed);
  }

  @Test
  void updateChangesTheDateAndBalanceOfTheSameRow() {
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            holding.getId(), snapshot.getId(), LocalDate.of(2026, 3, 30), new BigDecimal("90.00"));

    assertThat(updated.getId()).isEqualTo(snapshot.getId());
    assertThat(updated.getDate()).isEqualTo(LocalDate.of(2026, 3, 30));
    assertThat(updated.getBalance()).isEqualByComparingTo("90.00");
    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(1);
  }

  @Test
  void updateKeepingTheSameDateIsNotADateConflict() {
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            holding.getId(), snapshot.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1.00"));

    assertThat(updated.getBalance()).isEqualByComparingTo("1.00");
  }

  @Test
  void updateOntoADateThatAlreadyHasASnapshotIsRejectedAndChangesNothing() {
    recordSnapshot(LocalDate.of(2026, 3, 30), "50.00");
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    assertThatThrownBy(
            () ->
                service.update(
                    holding.getId(),
                    snapshot.getId(),
                    LocalDate.of(2026, 3, 30),
                    new BigDecimal("1.00")))
        .isInstanceOf(InvestmentSnapshotDateTakenException.class);

    InvestmentSnapshot stored = snapshotRepository.findById(snapshot.getId()).orElseThrow();
    assertThat(stored.getDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(stored.getBalance()).isEqualByComparingTo("100.00");
  }

  @Test
  void updateAllowsTheSameDateAsASnapshotOfAnotherHolding() {
    InvestmentHolding other = holdingRepository.save(InvestmentHoldingMother.holding().build());
    service.record(other.getId(), LocalDate.of(2026, 3, 30), BigDecimal.TEN);
    InvestmentSnapshot snapshot = recordSnapshot(LocalDate.of(2026, 3, 31), "100.00");

    InvestmentSnapshot updated =
        service.update(
            holding.getId(), snapshot.getId(), LocalDate.of(2026, 3, 30), BigDecimal.ONE);

    assertThat(updated.getDate()).isEqualTo(LocalDate.of(2026, 3, 30));
  }

  @Test
  void updateRejectsAnUnknownSnapshotOrOneOfAnotherHolding() {
    InvestmentHolding other = holdingRepository.save(InvestmentHoldingMother.holding().build());
    InvestmentSnapshot ofOther =
        service.record(other.getId(), LocalDate.of(2026, 3, 31), BigDecimal.TEN).snapshot();

    assertThatThrownBy(
            () ->
                service.update(holding.getId(), UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThatThrownBy(
            () -> service.update(holding.getId(), ofOther.getId(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThat(snapshotRepository.findById(ofOther.getId()).orElseThrow().getBalance())
        .isEqualByComparingTo("10");
  }

  @Test
  void updateRejectsAnUnknownHolding() {
    assertThatThrownBy(
            () ->
                service.update(
                    UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), BigDecimal.ONE))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void updateOfAClosedHoldingIsRejectedWhenItWouldLeaveANonZeroLatestSnapshot() {
    InvestmentHolding closed = closedHolding();
    service.record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"));
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    assertThatThrownBy(
            () ->
                service.update(
                    closed.getId(), zero.getId(), LocalDate.of(2026, 4, 30), new BigDecimal("5")))
        .isInstanceOf(InvestmentSnapshotClosedHoldingException.class);
    // moving the zero snapshot before the non-zero one makes the non-zero one the latest
    assertThatThrownBy(
            () ->
                service.update(
                    closed.getId(), zero.getId(), LocalDate.of(2026, 3, 1), BigDecimal.ZERO))
        .isInstanceOf(InvestmentSnapshotClosedHoldingException.class);
    InvestmentSnapshot stored = snapshotRepository.findById(zero.getId()).orElseThrow();
    assertThat(stored.getBalance()).isEqualByComparingTo("0");
    assertThat(stored.getDate()).isEqualTo(LocalDate.of(2026, 4, 30));
  }

  @Test
  void updateOfAClosedHoldingIsAllowedWhenTheLatestSnapshotStaysZero() {
    InvestmentHolding closed = closedHolding();
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

    service.delete(holding.getId(), gone.getId());

    assertThat(snapshotRepository.findByHoldingId(holding.getId()))
        .extracting(InvestmentSnapshot::getId)
        .containsExactly(keep.getId());
  }

  @Test
  void deleteRejectsAnUnknownSnapshotOrOneOfAnotherHolding() {
    InvestmentHolding other = holdingRepository.save(InvestmentHoldingMother.holding().build());
    InvestmentSnapshot ofOther =
        service.record(other.getId(), LocalDate.of(2026, 3, 31), BigDecimal.TEN).snapshot();

    assertThatThrownBy(() -> service.delete(holding.getId(), UUID.randomUUID()))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThatThrownBy(() -> service.delete(holding.getId(), ofOther.getId()))
        .isInstanceOf(InvestmentSnapshotNotFoundException.class);
    assertThat(snapshotRepository.findById(ofOther.getId())).isPresent();
  }

  @Test
  void deleteRejectsAnUnknownHolding() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void deleteOfAClosedHoldingIsRejectedWhenTheRemainingLatestSnapshotIsNonZero() {
    InvestmentHolding closed = closedHolding();
    service.record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"));
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    assertThatThrownBy(() -> service.delete(closed.getId(), zero.getId()))
        .isInstanceOf(InvestmentSnapshotClosedHoldingException.class);
    assertThat(snapshotRepository.findById(zero.getId())).isPresent();
  }

  @Test
  void deleteOfAClosedHoldingIsAllowedWhenTheRemainingLatestIsZeroOrNone() {
    InvestmentHolding closed = closedHolding();
    InvestmentSnapshot old =
        service
            .record(closed.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("100.00"))
            .snapshot();
    InvestmentSnapshot zero =
        service.record(closed.getId(), LocalDate.of(2026, 4, 30), BigDecimal.ZERO).snapshot();

    service.delete(closed.getId(), old.getId());
    service.delete(closed.getId(), zero.getId());

    assertThat(snapshotRepository.findByHoldingId(closed.getId())).isEmpty();
  }

  @Test
  void recordRecordsACreateAuditEntryOnFirstInsert() {
    RecordedSnapshot recorded =
        service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1234.56"));

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityType())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditEntityType.INVESTMENT_SNAPSHOT);
    assertThat(entry.entityId()).isEqualTo(recorded.snapshot().getId());
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void recordRecordsAnUpdateAuditEntryOnSameDayReplace() {
    service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1000.00"));
    auditLog.entries().clear();

    service.record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1200.00"));

    var entry = auditLog.onlyEntry();
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
    assertThat(entry.changes()).containsKey("balance");
  }

  @Test
  void updateRecordsAnUpdateAuditEntry() {
    InvestmentSnapshot created =
        service
            .record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1000.00"))
            .snapshot();
    auditLog.entries().clear();

    service.update(holding.getId(), created.getId(), LocalDate.of(2026, 4, 1), BigDecimal.TEN);

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    InvestmentSnapshot created =
        service
            .record(holding.getId(), LocalDate.of(2026, 3, 31), new BigDecimal("1000.00"))
            .snapshot();
    auditLog.entries().clear();

    service.delete(holding.getId(), created.getId());

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
  }
}
