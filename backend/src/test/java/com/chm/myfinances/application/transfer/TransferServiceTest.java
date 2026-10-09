package com.chm.myfinances.application.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.TradeSide;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransferRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Application-layer tests for {@link TransferService}, written first (ADR 0004) against
 * hand-written fakes for every repository port it depends on - plain JUnit, no Spring context.
 * Covers F005 spec's "rejects closed-account transfers" and plan.md's verification bullet on
 * same-account/closed-account rejection.
 */
class TransferServiceTest {

  private final FakeTransferRepository transferRepository = new FakeTransferRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final TransferService service = serviceWith(idGenerator);

  private TransferService serviceWith(FakeIdGenerator generator) {
    FakeInvestmentSnapshotRepository snapshotRepository = new FakeInvestmentSnapshotRepository();
    return new TransferService(
        transferRepository,
        accountRepository,
        holdingRepository,
        new InvestmentSnapshotService(snapshotRepository, holdingRepository, generator),
        new InvestmentHoldingService(
            holdingRepository,
            productRepository,
            accountRepository,
            id -> false,
            new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository),
            generator,
            Clock.systemUTC()),
        generator,
        new AuditRecorder(auditLog));
  }

  private Account checking;
  private Account savings;
  private Account closedAccount;

  @BeforeEach
  void setUp() {
    checking = accountRepository.save(AccountMother.checking().build());
    savings = accountRepository.save(AccountMother.savings().build());
    closedAccount = accountRepository.save(AccountMother.checking().withName("Old").build());
    closedAccount.close(LocalDate.now());
    accountRepository.save(closedAccount);
  }

  @Test
  void createAssignsIdFromIdGenerator() {
    UUID nextId = UUID.randomUUID();
    TransferService service = serviceWith(new FakeIdGenerator(nextId));

    Transfer created =
        service.create(
            LocalDate.of(2026, 3, 15),
            checking.getId(),
            savings.getId(),
            new BigDecimal("42.50"),
            "Move to savings",
            null);

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(transferRepository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsUnknownFromAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    UUID.randomUUID(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsUnknownToAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    checking.getId(),
                    UUID.randomUUID(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsAClosedFromAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    closedAccount.getId(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void createRejectsAClosedToAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    checking.getId(),
                    closedAccount.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void createRejectsTheSameAccountOnBothSides() {
    assertThatThrownBy(
            () ->
                service.create(
                    LocalDate.now(),
                    checking.getId(),
                    checking.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(SameAccountTransferException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(TransferNotFoundException.class);
  }

  @Test
  void editUpdatesFields() {
    Transfer created =
        service.create(
            LocalDate.of(2026, 1, 1),
            checking.getId(),
            savings.getId(),
            BigDecimal.TEN,
            "Original description",
            "Original note");

    Transfer edited =
        service.edit(
            created.getId(),
            LocalDate.of(2026, 2, 2),
            savings.getId(),
            checking.getId(),
            new BigDecimal("20.00"),
            "Edited description",
            "Edited note");

    assertThat(edited.getDate()).isEqualTo(LocalDate.of(2026, 2, 2));
    assertThat(edited.getFromAccountId()).isEqualTo(savings.getId());
    assertThat(edited.getToAccountId()).isEqualTo(checking.getId());
    assertThat(edited.getAmount()).isEqualByComparingTo("20.00");
    assertThat(edited.getDescription()).isEqualTo("Edited description");
    assertThat(edited.getAdditionalNotes()).isEqualTo("Edited note");
  }

  @Test
  void editOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(
            () ->
                service.edit(
                    UUID.randomUUID(),
                    LocalDate.now(),
                    checking.getId(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(TransferNotFoundException.class);
  }

  @Test
  void editRejectsMovingATransferOntoAClosedAccount() {
    Transfer created =
        service.create(
            LocalDate.now(), checking.getId(), savings.getId(), BigDecimal.TEN, "Transfer", null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    closedAccount.getId(),
                    savings.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void editRejectsTheSameAccountOnBothSides() {
    Transfer created =
        service.create(
            LocalDate.now(), checking.getId(), savings.getId(), BigDecimal.TEN, "Transfer", null);

    assertThatThrownBy(
            () ->
                service.edit(
                    created.getId(),
                    LocalDate.now(),
                    checking.getId(),
                    checking.getId(),
                    BigDecimal.TEN,
                    "Transfer",
                    null))
        .isInstanceOf(SameAccountTransferException.class);
  }

  @Test
  void deleteRemovesTheTransfer() {
    Transfer created =
        service.create(
            LocalDate.now(), checking.getId(), savings.getId(), BigDecimal.TEN, "Transfer", null);

    service.delete(created.getId());

    assertThat(transferRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(TransferNotFoundException.class);
  }

  @Test
  void findAllDelegatesToRepositoryWithFilterAndPageable() {
    service.create(
        LocalDate.of(2026, 1, 1), checking.getId(), savings.getId(), BigDecimal.TEN, "First", null);
    service.create(
        LocalDate.of(2026, 2, 1),
        savings.getId(),
        checking.getId(),
        BigDecimal.TEN,
        "Second",
        null);

    Page<Transfer> page =
        service.findAll(
            new TransferFilter(null, null, savings.getId(), null), PageRequest.of(0, 20));

    assertThat(page.getTotalElements()).isEqualTo(2);
  }

  // --- requireExactlyOneRequestShape (F027 spec, ADR 0024) -------------------------------------

  private static final List<TradeConfirmationLine> ONE_LINE =
      List.of(
          new TradeConfirmationLine(
              UUID.randomUUID(), TradeSide.BUY, BigDecimal.ONE, BigDecimal.TEN, null, false));

  @Test
  void requireExactlyOneRequestShapeAcceptsThePlainShapeAlone() {
    TransferService.requireExactlyOneRequestShape(
        UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, null, null, null);
  }

  @Test
  void requireExactlyOneRequestShapeAcceptsTheConfirmationShapeAlone() {
    TransferService.requireExactlyOneRequestShape(
        null, null, null, UUID.randomUUID(), UUID.randomUUID(), ONE_LINE);
  }

  @Test
  void requireExactlyOneRequestShapeRejectsNeitherShape() {
    assertThatThrownBy(
            () -> TransferService.requireExactlyOneRequestShape(null, null, null, null, null, null))
        .isInstanceOf(InvalidTradeConfirmationException.class);
  }

  @Test
  void requireExactlyOneRequestShapeRejectsBothShapes() {
    assertThatThrownBy(
            () ->
                TransferService.requireExactlyOneRequestShape(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    BigDecimal.TEN,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    ONE_LINE))
        .isInstanceOf(InvalidTradeConfirmationException.class);
  }

  @Test
  void requireExactlyOneRequestShapeRejectsAPartialPlainShape() {
    assertThatThrownBy(
            () ->
                TransferService.requireExactlyOneRequestShape(
                    UUID.randomUUID(), null, BigDecimal.TEN, null, null, null))
        .isInstanceOf(InvalidTradeConfirmationException.class);
  }

  @Test
  void requireExactlyOneRequestShapeRejectsAPartialConfirmationShape() {
    assertThatThrownBy(
            () ->
                TransferService.requireExactlyOneRequestShape(
                    null, null, null, UUID.randomUUID(), null, ONE_LINE))
        .isInstanceOf(InvalidTradeConfirmationException.class);
  }

  @Test
  void requireExactlyOneRequestShapeRejectsEmptyLines() {
    assertThatThrownBy(
            () ->
                TransferService.requireExactlyOneRequestShape(
                    null, null, null, UUID.randomUUID(), UUID.randomUUID(), List.of()))
        .isInstanceOf(InvalidTradeConfirmationException.class);
  }

  @Test
  void createRecordsACreateAuditEntry() {
    Transfer created =
        service.create(
            LocalDate.of(2026, 3, 15),
            checking.getId(),
            savings.getId(),
            new BigDecimal("42.50"),
            "Move to savings",
            null);

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityType()).isEqualTo(AuditEntityType.TRANSFER);
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.entityLabel()).isEqualTo("Move to savings");
    assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
    assertThat(entry.origin()).isEqualTo(AuditOrigin.USER);
  }

  @Test
  void editRecordsAnUpdateAuditEntry() {
    Transfer created =
        service.create(
            LocalDate.now(), checking.getId(), savings.getId(), BigDecimal.TEN, "Move", null);
    auditLog.entries().clear();

    service.edit(
        created.getId(),
        LocalDate.now(),
        checking.getId(),
        savings.getId(),
        new BigDecimal("20"),
        "Move",
        null);

    var entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
    assertThat(entry.changes()).containsKey("amount");
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    Transfer created =
        service.create(
            LocalDate.now(), checking.getId(), savings.getId(), BigDecimal.TEN, "Move", null);
    auditLog.entries().clear();

    service.delete(created.getId());

    var entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.DELETE);
    assertThat(entry.entityLabel()).isEqualTo("Move");
  }
}
