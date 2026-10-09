package com.chm.myfinances.application.investmentsegment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentProductRepository;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentSegmentRepository;
import com.chm.myfinances.testsupport.mothers.InvestmentProductMother;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link InvestmentSegmentService}, written first (ADR 0004) against
 * hand-written fakes - plain JUnit, no Spring context (F026 spec).
 */
class InvestmentSegmentServiceTest {

  private final FakeInvestmentSegmentRepository segmentRepository =
      new FakeInvestmentSegmentRepository();
  private final FakeInvestmentProductRepository productRepository =
      new FakeInvestmentProductRepository();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final InvestmentSegmentService service =
      new InvestmentSegmentService(
          segmentRepository, productRepository, new FakeIdGenerator(), new AuditRecorder(auditLog));

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    InvestmentSegmentService service =
        new InvestmentSegmentService(
            segmentRepository,
            productRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog));

    InvestmentSegment created = service.create("Shoppings");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Shoppings");
    assertThat(segmentRepository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Shoppings");

    assertThatThrownBy(() -> service.create("Shoppings"))
        .isInstanceOf(InvestmentSegmentNameAlreadyExistsException.class);
  }

  @Test
  void findAllReturnsEverySegment() {
    service.create("Shoppings");
    service.create("Logistica");

    assertThat(service.findAll()).hasSize(2);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(InvestmentSegmentNotFoundException.class);
  }

  @Test
  void renameChangesTheName() {
    InvestmentSegment created = service.create("Shoppings");

    InvestmentSegment renamed = service.rename(created.getId(), "Shoppings e Lajes");

    assertThat(renamed.getName()).isEqualTo("Shoppings e Lajes");
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    InvestmentSegment created = service.create("Shoppings");

    assertThat(service.rename(created.getId(), "Shoppings").getName()).isEqualTo("Shoppings");
  }

  @Test
  void renameRejectsAnotherSegmentsName() {
    service.create("Shoppings");
    InvestmentSegment logistica = service.create("Logistica");

    assertThatThrownBy(() -> service.rename(logistica.getId(), "Shoppings"))
        .isInstanceOf(InvestmentSegmentNameAlreadyExistsException.class);
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "Anything"))
        .isInstanceOf(InvestmentSegmentNotFoundException.class);
  }

  @Test
  void deleteRemovesAnUnreferencedSegment() {
    InvestmentSegment created = service.create("Shoppings");

    service.delete(created.getId());

    assertThat(segmentRepository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(InvestmentSegmentNotFoundException.class);
  }

  @Test
  void deleteIsBlockedWhileAProductUsesIt() {
    InvestmentSegment created = service.create("Shoppings");
    productRepository.save(
        InvestmentProductMother.product()
            .withName("KNRI11 Test")
            .withSegmentId(created.getId())
            .build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(InvestmentSegmentInUseException.class);
    assertThat(segmentRepository.findById(created.getId())).isPresent();
  }
}
