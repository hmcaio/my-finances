package com.chm.myfinances.application.paymentmethod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link PaymentMethodService}, against hand-written fakes — plain
 * JUnit, no Spring context (ADR 0004). Per F002 plan.md, the referenced-by-transaction delete guard
 * was deferred until F004 (Transactions) existed to check against — it's exercised here now via
 * {@link FakeTransactionRepository}.
 */
class PaymentMethodServiceTest {

  private final FakePaymentMethodRepository repository = new FakePaymentMethodRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final PaymentMethodService service =
      new PaymentMethodService(
          repository, transactionRepository, idGenerator, new AuditRecorder(auditLog));

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    PaymentMethodService service =
        new PaymentMethodService(
            repository,
            transactionRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog));

    PaymentMethod created = service.create("Debit Card");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Debit Card");
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Debit Card");

    assertThatThrownBy(() -> service.create("Debit Card"))
        .isInstanceOf(PaymentMethodNameAlreadyExistsException.class);
  }

  @Test
  void findAllReturnsEveryPersistedPaymentMethod() {
    service.create("Debit Card");
    service.create("Cash");

    List<PaymentMethod> all = service.findAll();

    assertThat(all)
        .extracting(PaymentMethod::getName)
        .containsExactlyInAnyOrder("Debit Card", "Cash");
  }

  @Test
  void renameUpdatesTheName() {
    PaymentMethod created = service.create("Debit Card");

    PaymentMethod renamed = service.rename(created.getId(), "Debit Card (Itau)");

    assertThat(renamed.getName()).isEqualTo("Debit Card (Itau)");
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    PaymentMethod created = service.create("Debit Card");

    PaymentMethod renamed = service.rename(created.getId(), "Debit Card");

    assertThat(renamed.getName()).isEqualTo("Debit Card");
  }

  @Test
  void renameRejectsADuplicateName() {
    service.create("Debit Card");
    PaymentMethod cash = service.create("Cash");

    assertThatThrownBy(() -> service.rename(cash.getId(), "Debit Card"))
        .isInstanceOf(PaymentMethodNameAlreadyExistsException.class);
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "New name"))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }

  @Test
  void deleteRemovesThePaymentMethod() {
    PaymentMethod created = service.create("Temp");

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(PaymentMethodNotFoundException.class);
  }

  @Test
  void deleteRejectsAPaymentMethodReferencedByATransaction() {
    // F002 plan.md's deferred delete guard: a payment method with existing transactions must not
    // be hard-deletable, since that would orphan those transactions' payment method reference.
    PaymentMethod created = service.create("Debit Card");
    transactionRepository.save(
        TransactionMother.expense()
            .withAmount(BigDecimal.TEN)
            .withPaymentMethodId(created.getId())
            .withDescription("In-use transaction")
            .build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(PaymentMethodInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void createRecordsACreateAuditEntry() {
    PaymentMethod created = service.create("Debit Card");

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.entityLabel()).isEqualTo("Debit Card");
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void renameRecordsAnUpdateAuditEntry() {
    PaymentMethod created = service.create("Debit Card");
    auditLog.entries().clear();

    service.rename(created.getId(), "Debit");

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    PaymentMethod created = service.create("Debit Card");
    auditLog.entries().clear();

    service.delete(created.getId());

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
  }
}
