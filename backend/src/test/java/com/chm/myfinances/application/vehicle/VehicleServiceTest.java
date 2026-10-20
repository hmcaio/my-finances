package com.chm.myfinances.application.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.domain.transaction.FuelDetails;
import com.chm.myfinances.domain.transaction.FuelType;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link VehicleService}, against hand-written fakes - plain JUnit, no
 * Spring context (ADR 0004). Mirrors {@code PaymentMethodServiceTest}.
 */
class VehicleServiceTest {

  private final FakeVehicleRepository repository = new FakeVehicleRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final VehicleService service =
      new VehicleService(
          repository,
          transactionRepository,
          idGenerator,
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    VehicleService service =
        new VehicleService(
            repository,
            transactionRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog, AuditReferenceLabels.none()));

    Vehicle created = service.create("Civic");

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Civic");
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Civic");

    assertThatThrownBy(() -> service.create("Civic"))
        .isInstanceOf(VehicleNameAlreadyExistsException.class);
  }

  @Test
  void findAllReturnsEveryPersistedVehicle() {
    service.create("Civic");
    service.create("Corolla");

    List<Vehicle> all = service.findAll();

    assertThat(all).extracting(Vehicle::getName).containsExactlyInAnyOrder("Civic", "Corolla");
  }

  @Test
  void findByIdReturnsTheVehicle() {
    Vehicle created = service.create("Civic");

    assertThat(service.findById(created.getId()).getName()).isEqualTo("Civic");
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void renameUpdatesTheName() {
    Vehicle created = service.create("Civic");

    Vehicle renamed = service.rename(created.getId(), "Civic 2019");

    assertThat(renamed.getName()).isEqualTo("Civic 2019");
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    Vehicle created = service.create("Civic");

    Vehicle renamed = service.rename(created.getId(), "Civic");

    assertThat(renamed.getName()).isEqualTo("Civic");
  }

  @Test
  void renameRejectsADuplicateName() {
    service.create("Civic");
    Vehicle corolla = service.create("Corolla");

    assertThatThrownBy(() -> service.rename(corolla.getId(), "Civic"))
        .isInstanceOf(VehicleNameAlreadyExistsException.class);
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "New name"))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void deleteRemovesTheVehicle() {
    Vehicle created = service.create("Temp");

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(VehicleNotFoundException.class);
  }

  @Test
  void deleteRejectsAVehicleReferencedByAFuelTransaction() {
    Vehicle created = service.create("Civic");
    FuelDetails fuelDetails =
        new FuelDetails(
            created.getId(), FuelType.GASOLINA, BigDecimal.TEN, BigDecimal.ONE, null, null);
    transactionRepository.save(
        TransactionMother.expense()
            .withAmount(BigDecimal.TEN)
            .withDescription("Fuel")
            .withFuelDetails(fuelDetails)
            .build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(VehicleInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void createRecordsACreateAuditEntry() {
    Vehicle created = service.create("Civic");

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.entityLabel()).isEqualTo("Civic");
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void renameRecordsAnUpdateAuditEntry() {
    Vehicle created = service.create("Civic");
    auditLog.entries().clear();

    service.rename(created.getId(), "Civic Hatch");

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    Vehicle created = service.create("Civic");
    auditLog.entries().clear();

    service.delete(created.getId());

    assertThat(auditLog.onlyEntry().action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
  }
}
