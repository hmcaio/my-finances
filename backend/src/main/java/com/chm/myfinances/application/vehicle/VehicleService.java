package com.chm.myfinances.application.vehicle;

import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.shared.IdGenerator;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for {@link Vehicle}: create/rename/findAll/findById/delete (F024 spec). New ids come
 * from the {@link IdGenerator} port (ADR 0005). Same shape as {@code PaymentMethodService}.
 *
 * <p>Delete is a 409 ({@link VehicleInUseException}) while any transaction's fuel details reference
 * the vehicle ({@link TransactionRepository#existsByFuelDetailsVehicleId}) - same
 * referenced-by-transaction pattern as {@code PaymentMethodService}/{@code CategoryService}.
 *
 * <p>Create/rename reject a duplicate name (409, {@link VehicleNameAlreadyExistsException}) - exact
 * match, case-sensitive, backed by {@code vehicles.name UNIQUE} ({@code V18}).
 */
@Service
public class VehicleService {

  private final VehicleRepository vehicleRepository;
  private final TransactionRepository transactionRepository;
  private final IdGenerator idGenerator;
  private final AuditRecorder auditRecorder;

  public VehicleService(
      VehicleRepository vehicleRepository,
      TransactionRepository transactionRepository,
      IdGenerator idGenerator,
      AuditRecorder auditRecorder) {
    this.vehicleRepository = vehicleRepository;
    this.transactionRepository = transactionRepository;
    this.idGenerator = idGenerator;
    this.auditRecorder = auditRecorder;
  }

  @Transactional
  public Vehicle create(String name) {
    if (vehicleRepository.existsByName(name)) {
      throw new VehicleNameAlreadyExistsException(name);
    }
    Vehicle vehicle = Vehicle.create(idGenerator.newId(), name);
    Vehicle saved = vehicleRepository.save(vehicle);
    auditRecorder.recordCreate(
        AuditEntityType.VEHICLE, saved.getId(), saved.getName(), saved.toAuditSnapshot());
    return saved;
  }

  public List<Vehicle> findAll() {
    return vehicleRepository.findAll();
  }

  /** Resolves a vehicle or throws 404 - used by the fuel-history endpoint to validate the id. */
  public Vehicle findById(UUID id) {
    return vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
  }

  @Transactional
  public Vehicle rename(UUID id, String newName) {
    Vehicle vehicle =
        vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    Map<String, Object> before = vehicle.toAuditSnapshot();
    if (vehicleRepository.existsByNameAndIdNot(newName, id)) {
      throw new VehicleNameAlreadyExistsException(newName);
    }
    vehicle.rename(newName);
    Vehicle saved = vehicleRepository.save(vehicle);
    auditRecorder.recordUpdate(
        AuditEntityType.VEHICLE, saved.getId(), saved.getName(), before, saved.toAuditSnapshot());
    return saved;
  }

  @Transactional
  public void delete(UUID id) {
    Vehicle vehicle =
        vehicleRepository.findById(id).orElseThrow(() -> new VehicleNotFoundException(id));
    if (transactionRepository.existsByFuelDetailsVehicleId(id)) {
      throw new VehicleInUseException(id);
    }
    vehicleRepository.deleteById(id);
    auditRecorder.recordDelete(
        AuditEntityType.VEHICLE, vehicle.getId(), vehicle.getName(), vehicle.toAuditSnapshot());
  }
}
