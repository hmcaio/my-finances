package com.chm.myfinances.application.investmentholding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.investmentproduct.InvestmentAccountRequiredException;
import com.chm.myfinances.application.investmentproduct.InvestmentProductNotFoundException;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeHasHoldingHistoryChecker;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentHoldingService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F022 spec, ADR 0020).
 */
class InvestmentHoldingServiceTest {

  private final FakeInvestmentHoldingRepository holdingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakeHasHoldingHistoryChecker historyChecker = new FakeHasHoldingHistoryChecker();
  private final FakeInvestmentSnapshotRepository snapshotRepository =
      new FakeInvestmentSnapshotRepository();
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery =
      new LatestInvestmentSnapshotQuery(snapshotRepository, holdingRepository);
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final InvestmentHoldingService service =
      new InvestmentHoldingService(
          holdingRepository,
          productRepository,
          accountRepository,
          historyChecker,
          latestSnapshotQuery,
          idGenerator,
          Clock.systemDefaultZone(),
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

  private final UUID xpAccountId =
      accountRepository.save(AccountMother.investment().withName("XP Test").build()).getId();
  private final UUID nuAccountId =
      accountRepository.save(AccountMother.investment().withName("Nubank Test").build()).getId();
  private final UUID productId =
      productRepository.save(InvestmentProductMother.product().build()).getId();

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InvestmentHoldingService service =
        new InvestmentHoldingService(
            holdingRepository,
            productRepository,
            accountRepository,
            historyChecker,
            latestSnapshotQuery,
            new FakeIdGenerator(nextId),
            Clock.systemDefaultZone(),
            new AuditRecorder(auditLog, AuditReferenceLabels.none()));

    InvestmentHolding created = service.create(productId, xpAccountId, "bought via promo");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getProductId()).isEqualTo(productId);
    assertThat(created.getAccountId()).isEqualTo(xpAccountId);
    assertThat(created.getAdditionalNotes()).isEqualTo("bought via promo");
    assertThat(created.isClosed()).isFalse();
  }

  @Test
  void createRejectsAnUnknownProduct() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), xpAccountId, null))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void createRejectsAnUnknownAccount() {
    assertThatThrownBy(() -> service.create(productId, UUID.randomUUID(), null))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsANonInvestmentAccount() {
    UUID checkingId = accountRepository.save(AccountMother.checking().build()).getId();

    assertThatThrownBy(() -> service.create(productId, checkingId, null))
        .isInstanceOf(InvestmentAccountRequiredException.class);
  }

  @Test
  void createRejectsAClosedInvestmentAccount() {
    Account closed = accountRepository.findById(xpAccountId).orElseThrow();
    closed.close(LocalDate.now());
    accountRepository.save(closed);

    assertThatThrownBy(() -> service.create(productId, xpAccountId, null))
        .isInstanceOf(InvestmentAccountRequiredException.class);
  }

  @Test
  void createRejectsADuplicatePair() {
    service.create(productId, xpAccountId, null);

    assertThatThrownBy(() -> service.create(productId, xpAccountId, null))
        .isInstanceOf(InvestmentHoldingAlreadyExistsException.class);
  }

  @Test
  void theSamePairInAnotherAccountIsAllowed() {
    service.create(productId, xpAccountId, null);

    InvestmentHolding second = service.create(productId, nuAccountId, null);

    assertThat(second.getAccountId()).isEqualTo(nuAccountId);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void findByProductListsEveryHoldingOfTheProduct() {
    InvestmentHolding atXp = service.create(productId, xpAccountId, null);
    InvestmentHolding atNu = service.create(productId, nuAccountId, null);

    assertThat(service.findByProduct(productId))
        .extracting(InvestmentHolding::getId)
        .containsExactlyInAnyOrder(atXp.getId(), atNu.getId());
  }

  @Test
  void findByProductOfUnknownProductThrowsNotFound() {
    assertThatThrownBy(() -> service.findByProduct(UUID.randomUUID()))
        .isInstanceOf(InvestmentProductNotFoundException.class);
  }

  @Test
  void findByAccountListsEveryHoldingInTheAccount() {
    InvestmentProduct otherProduct =
        productRepository.save(InvestmentProductMother.product().withName("Other").build());
    InvestmentHolding first = service.create(productId, xpAccountId, null);
    InvestmentHolding second = service.create(otherProduct.getId(), xpAccountId, null);

    assertThat(service.findByAccount(xpAccountId))
        .extracting(InvestmentHolding::getId)
        .containsExactlyInAnyOrder(first.getId(), second.getId());
  }

  @Test
  void findByAccountOfUnknownAccountThrowsNotFound() {
    assertThatThrownBy(() -> service.findByAccount(UUID.randomUUID()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void editNotesReplacesTheNotes() {
    InvestmentHolding created = service.create(productId, xpAccountId, "old note");

    InvestmentHolding edited = service.editNotes(created.getId(), "new note");

    assertThat(edited.getAdditionalNotes()).isEqualTo("new note");
  }

  @Test
  void closeSetsTheClosedDate() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);

    InvestmentHolding closed = service.close(created.getId());

    assertThat(closed.isClosed()).isTrue();
    assertThat(holdingRepository.findById(created.getId()).orElseThrow().isClosed()).isTrue();
  }

  @Test
  void closeOfAnAlreadyClosedHoldingThrowsConflict() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    service.close(created.getId());

    assertThatThrownBy(() -> service.close(created.getId()))
        .isInstanceOf(InvestmentHoldingAlreadyClosedException.class);
  }

  private void snapshot(InvestmentHolding holding, LocalDate date, String balance) {
    snapshotRepository.save(
        InvestmentSnapshot.create(
            UUID.randomUUID(), holding.getId(), date, new BigDecimal(balance)));
  }

  @Test
  void closeIsBlockedWhileTheLatestSnapshotIsNonZero() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    snapshot(created, LocalDate.of(2026, 3, 31), "150.00");

    assertThatThrownBy(() -> service.close(created.getId()))
        .isInstanceOf(InvestmentHoldingNotEmptyException.class);
    assertThat(holdingRepository.findById(created.getId()).orElseThrow().isClosed()).isFalse();
  }

  @Test
  void closeIsAllowedWhenTheLatestSnapshotIsZeroEvenAfterAnEarlierNonZeroOne() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    snapshot(created, LocalDate.of(2026, 3, 31), "150.00");
    snapshot(created, LocalDate.of(2026, 4, 30), "0.00");

    assertThat(service.close(created.getId()).isClosed()).isTrue();
  }

  @Test
  void closingOneHoldingDoesNotAffectAnotherHoldingOfTheSameProduct() {
    InvestmentHolding atXp = service.create(productId, xpAccountId, null);
    InvestmentHolding atNu = service.create(productId, nuAccountId, null);

    service.close(atXp.getId());

    assertThat(holdingRepository.findById(atNu.getId()).orElseThrow().isClosed()).isFalse();
  }

  @Test
  void closeOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.close(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void deleteRemovesAHoldingWithZeroHistory() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);

    service.delete(created.getId());

    assertThat(holdingRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteIsBlockedOnceTheHistoryCheckerReportsHistory() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    historyChecker.markHasHistory(created.getId());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentHoldingHasHistoryException.class);
    assertThat(holdingRepository.findById(created.getId())).isPresent();
  }

  @Test
  void aHoldingWithHistoryCanStillBeClosed() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    historyChecker.markHasHistory(created.getId());

    assertThat(service.close(created.getId()).isClosed()).isTrue();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void hasHistoryDelegatesToTheChecker() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    assertThat(service.hasHistory(created.getId())).isFalse();

    historyChecker.markHasHistory(created.getId());

    assertThat(service.hasHistory(created.getId())).isTrue();
  }

  @Test
  void hasHistoryOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.hasHistory(UUID.randomUUID()))
        .isInstanceOf(InvestmentHoldingNotFoundException.class);
  }

  @Test
  void createRecordsACreateAuditEntry() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityType())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditEntityType.INVESTMENT_HOLDING);
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void closeRecordsACloseAuditEntry() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    auditLog.entries().clear();

    service.close(created.getId());

    var entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CLOSE);
    assertThat(entry.changes()).containsKey("closedDate");
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    InvestmentHolding created = service.create(productId, xpAccountId, null);
    auditLog.entries().clear();

    service.delete(created.getId());

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
  }
}
