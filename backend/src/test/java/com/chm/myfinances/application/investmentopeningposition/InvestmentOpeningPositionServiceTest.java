package com.chm.myfinances.application.investmentopeningposition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingClosedException;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentopeningposition.InvestmentOpeningPosition;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.TransferTradeLine;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentOpeningPositionRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferTradeLineRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentHoldingMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentOpeningPositionService} (F038, ADR 0033), written
 * first (ADR 0004) against hand-written fakes - plain JUnit, no Spring context.
 */
class InvestmentOpeningPositionServiceTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 10, 29);
  private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);

  private final FakeInvestmentOpeningPositionRepository positionRepository =
      new FakeInvestmentOpeningPositionRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeTransferTradeLineRepository tradeLineRepository =
      new FakeTransferTradeLineRepository();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final Clock clock = TestClocks.fixedAtStartOf(TODAY);
  private final AuditRecorder auditRecorder =
      new AuditRecorder(auditLog, AuditReferenceLabels.none());
  private final InvestmentOpeningPositionService service =
      new InvestmentOpeningPositionService(
          positionRepository,
          holdingRepository,
          tradeLineRepository,
          new InvestmentSnapshotService(
              snapshotRepository, holdingRepository, new FakeIdGenerator(), auditRecorder),
          new FakeIdGenerator(),
          clock,
          auditRecorder);

  private InvestmentHolding holding;

  @BeforeEach
  void setUp() {
    holding = holdingRepository.save(InvestmentHoldingMother.holding().build());
  }

  private RecordedOpeningPosition upsert(LocalDate asOf, String quantity, String unitPrice) {
    return service.upsert(
        holding.getId(), asOf, new BigDecimal(quantity), new BigDecimal(unitPrice), null);
  }

  private void addTrade(LocalDate date) {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            date,
            UUID.randomUUID(),
            holding.getAccountId(),
            holding.getProductId(),
            TradeSide.BUY,
            BigDecimal.TEN,
            BigDecimal.ONE,
            null));
  }

  // --- upsert: create / replace ---

  @Test
  void upsertCreatesAPositionAndAuditsACreate() {
    RecordedOpeningPosition recorded = upsert(AS_OF, "40", "150.00");

    assertThat(recorded.created()).isTrue();
    InvestmentOpeningPosition position = recorded.position();
    assertThat(position.getHoldingId()).isEqualTo(holding.getId());
    assertThat(position.getAsOfDate()).isEqualTo(AS_OF);
    assertThat(position.getQuantity()).isEqualByComparingTo("40");
    assertThat(position.getUnitPrice()).isEqualByComparingTo("150.00");
    assertThat(positionRepository.findByHoldingId(holding.getId())).isPresent();
    assertThat(auditLog.onlyEntry().entityType())
        .isEqualTo(AuditEntityType.INVESTMENT_OPENING_POSITION);
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.CREATE);
  }

  @Test
  void upsertTakesTheIdFromTheGenerator() {
    UUID nextId = UUID.randomUUID();
    InvestmentOpeningPositionService seeded =
        new InvestmentOpeningPositionService(
            positionRepository,
            holdingRepository,
            tradeLineRepository,
            new InvestmentSnapshotService(
                snapshotRepository, holdingRepository, new FakeIdGenerator(), auditRecorder),
            new FakeIdGenerator(nextId),
            clock,
            auditRecorder);

    RecordedOpeningPosition recorded =
        seeded.upsert(holding.getId(), AS_OF, BigDecimal.TEN, BigDecimal.ONE, null);

    assertThat(recorded.position().getId()).isEqualTo(nextId);
  }

  @Test
  void upsertOnAHoldingWithAPositionReplacesItInPlaceAndAuditsAnUpdate() {
    RecordedOpeningPosition first = upsert(AS_OF, "40", "150.00");
    auditLog.entries().clear();

    RecordedOpeningPosition second = upsert(LocalDate.of(2026, 9, 1), "50", "140.00");

    assertThat(second.created()).isFalse();
    assertThat(second.position().getId()).isEqualTo(first.position().getId());
    assertThat(positionRepository.findAll()).hasSize(1);
    assertThat(positionRepository.findByHoldingId(holding.getId()).orElseThrow().getQuantity())
        .isEqualByComparingTo("50");
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.UPDATE);
  }

  @Test
  void upsertWithIdenticalValuesWritesNoAuditEntry() {
    upsert(AS_OF, "40", "150.00");
    auditLog.entries().clear();

    upsert(AS_OF, "40.0", "150");

    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void upsertAcceptsTodayAsTheAsOfDate() {
    assertThat(upsert(TODAY, "1", "1").created()).isTrue();
  }

  // --- upsert: rejections ---

  @Test
  void upsertRejectsAnUnknownHolding() {
    assertThatThrownBy(
            () -> service.upsert(UUID.randomUUID(), AS_OF, BigDecimal.TEN, BigDecimal.ONE, null))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
    assertThat(positionRepository.findAll()).isEmpty();
  }

  @Test
  void upsertRejectsAClosedHolding() {
    holding.close(TODAY);
    holdingRepository.save(holding);

    assertThatThrownBy(() -> upsert(AS_OF, "40", "150"))
        .isInstanceOf(InvestmentHoldingClosedException.class);
    assertThat(positionRepository.findAll()).isEmpty();
  }

  @Test
  void upsertRejectsAFutureAsOfDate() {
    assertThatThrownBy(() -> upsert(TODAY.plusDays(1), "40", "150"))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThat(positionRepository.findAll()).isEmpty();
    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void upsertTranslatesEachDomainViolationToInvalidOpeningPosition() {
    assertThatThrownBy(() -> upsert(AS_OF, "0", "150"))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThatThrownBy(() -> upsert(AS_OF, "-1", "150"))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThatThrownBy(() -> upsert(AS_OF, "40", "0"))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThatThrownBy(() -> upsert(AS_OF, "40", "-0.01"))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThatThrownBy(
            () -> service.upsert(holding.getId(), null, BigDecimal.TEN, BigDecimal.ONE, null))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThatThrownBy(() -> service.upsert(holding.getId(), AS_OF, null, BigDecimal.ONE, null))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThat(positionRepository.findAll()).isEmpty();
  }

  @Test
  void upsertRejectsANegativeCurrentValueAsInvalidAndWritesNothing() {
    assertThatThrownBy(
            () ->
                service.upsert(
                    holding.getId(), AS_OF, BigDecimal.TEN, BigDecimal.ONE, new BigDecimal("-1")))
        .isInstanceOf(InvalidOpeningPositionException.class);
    assertThat(positionRepository.findAll()).isEmpty();
    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void upsertRejectsAnAsOfDateAfterTheHoldingsEarliestTrade() {
    addTrade(LocalDate.of(2026, 6, 1));
    addTrade(LocalDate.of(2026, 8, 1));

    assertThatThrownBy(() -> upsert(LocalDate.of(2026, 7, 1), "40", "150"))
        .isInstanceOf(OpeningPositionAfterTradesException.class);
    assertThat(positionRepository.findAll()).isEmpty();
  }

  @Test
  void upsertAcceptsTheSameDayAsTheEarliestTradeAndAnyEarlierDate() {
    addTrade(LocalDate.of(2026, 6, 1));

    assertThat(upsert(LocalDate.of(2026, 6, 1), "40", "150").created()).isTrue();
    assertThat(upsert(LocalDate.of(2026, 1, 1), "40", "150").created()).isFalse();
  }

  @Test
  void upsertIgnoresTradesOfAnotherHoldingOfTheSameProduct() {
    tradeLineRepository.add(
        new TransferTradeLine(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            UUID.randomUUID(),
            UUID.randomUUID(),
            holding.getProductId(),
            TradeSide.BUY,
            BigDecimal.TEN,
            BigDecimal.ONE,
            null));

    assertThat(upsert(AS_OF, "40", "150").created()).isTrue();
  }

  // --- upsert: the current value / snapshot ---

  @Test
  void upsertWithACurrentValueRecordsASnapshotDatedAsOf() {
    service.upsert(
        holding.getId(),
        AS_OF,
        new BigDecimal("40"),
        new BigDecimal("150"),
        new BigDecimal("6400"));

    InvestmentSnapshot snapshot = snapshotRepository.findByHoldingId(holding.getId()).get(0);
    assertThat(snapshot.getDate()).isEqualTo(AS_OF);
    assertThat(snapshot.getBalance()).isEqualByComparingTo("6400");
  }

  @Test
  void upsertWithACurrentValueReplacesASameDaySnapshot() {
    service.upsert(
        holding.getId(),
        AS_OF,
        new BigDecimal("40"),
        new BigDecimal("150"),
        new BigDecimal("6400"));

    service.upsert(
        holding.getId(),
        AS_OF,
        new BigDecimal("40"),
        new BigDecimal("150"),
        new BigDecimal("6500"));

    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(1);
    assertThat(snapshotRepository.findByHoldingId(holding.getId()).get(0).getBalance())
        .isEqualByComparingTo("6500");
  }

  @Test
  void upsertWithoutACurrentValueWritesNoSnapshot() {
    upsert(AS_OF, "40", "150");

    assertThat(snapshotRepository.findAll()).isEmpty();
  }

  @Test
  void upsertWithoutACurrentValueLeavesSnapshotsAloneEvenWhenTheDateMoves() {
    service.upsert(
        holding.getId(),
        AS_OF,
        new BigDecimal("40"),
        new BigDecimal("150"),
        new BigDecimal("6400"));

    upsert(LocalDate.of(2026, 9, 1), "40", "150");

    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(1);
    InvestmentSnapshot snapshot = snapshotRepository.findByHoldingId(holding.getId()).get(0);
    assertThat(snapshot.getDate()).isEqualTo(AS_OF);
    assertThat(snapshot.getBalance()).isEqualByComparingTo("6400");
  }

  // --- delete ---

  @Test
  void deleteRemovesThePositionAndAuditsADeleteButKeepsSnapshots() {
    service.upsert(
        holding.getId(),
        AS_OF,
        new BigDecimal("40"),
        new BigDecimal("150"),
        new BigDecimal("6400"));
    auditLog.entries().clear();

    service.delete(holding.getId());

    assertThat(positionRepository.findByHoldingId(holding.getId())).isEmpty();
    assertThat(snapshotRepository.findByHoldingId(holding.getId())).hasSize(1);
    assertThat(auditLog.onlyEntry().action()).isEqualTo(AuditAction.DELETE);
    assertThat(auditLog.onlyEntry().entityType())
        .isEqualTo(AuditEntityType.INVESTMENT_OPENING_POSITION);
  }

  @Test
  void deleteRejectsAnUnknownHolding() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void deleteRejectsAHoldingWithNoPosition() {
    assertThatThrownBy(() -> service.delete(holding.getId()))
        .isInstanceOf(InvestmentOpeningPositionNotFoundException.class);
  }

  @Test
  void deleteRejectsAClosedHolding() {
    upsert(AS_OF, "40", "150");
    holding.close(TODAY);
    holdingRepository.save(holding);

    assertThatThrownBy(() -> service.delete(holding.getId()))
        .isInstanceOf(InvestmentHoldingClosedException.class);
    assertThat(positionRepository.findByHoldingId(holding.getId())).isPresent();
  }

  // --- findByHoldingId ---

  @Test
  void findByHoldingIdReturnsThePosition() {
    upsert(AS_OF, "40", "150");

    assertThat(service.findByHoldingId(holding.getId()).getQuantity()).isEqualByComparingTo("40");
  }

  @Test
  void findByHoldingIdDistinguishesAnUnknownHoldingFromAMissingPosition() {
    assertThatThrownBy(() -> service.findByHoldingId(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
    assertThatThrownBy(() -> service.findByHoldingId(holding.getId()))
        .isInstanceOf(InvestmentOpeningPositionNotFoundException.class);
  }
}
