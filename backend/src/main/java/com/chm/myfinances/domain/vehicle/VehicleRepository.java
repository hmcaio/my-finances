package com.chm.myfinances.domain.vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Vehicle} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/vehicle}.
 */
public interface VehicleRepository {

  Vehicle save(Vehicle vehicle);

  Optional<Vehicle> findById(UUID id);

  List<Vehicle> findAll();

  void deleteById(UUID id);

  boolean existsById(UUID id);

  /** Whether a Vehicle already has this exact name - backs the create-time duplicate guard. */
  boolean existsByName(String name);

  /**
   * Whether a Vehicle other than {@code excludedId} already has this exact name - backs the
   * rename-time duplicate guard without rejecting a no-op rename to the vehicle's own current name.
   */
  boolean existsByNameAndIdNot(String name, UUID excludedId);
}
