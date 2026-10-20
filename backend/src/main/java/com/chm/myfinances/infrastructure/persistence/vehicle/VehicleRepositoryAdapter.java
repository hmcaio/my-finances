package com.chm.myfinances.infrastructure.persistence.vehicle;

import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing the domain's {@link VehicleRepository} port on top of Spring Data/Hibernate
 * (ADR 0004).
 */
@Component
public class VehicleRepositoryAdapter implements VehicleRepository {

  private final VehicleJpaRepository jpaRepository;

  public VehicleRepositoryAdapter(VehicleJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Vehicle save(Vehicle vehicle) {
    VehicleJpaEntity entity =
        jpaRepository
            .findById(vehicle.getId())
            .map(
                existing -> {
                  existing.setName(vehicle.getName());
                  return existing;
                })
            .orElseGet(() -> new VehicleJpaEntity(vehicle.getId(), vehicle.getName()));
    return toDomain(jpaRepository.save(entity));
  }

  @Override
  public Optional<Vehicle> findById(UUID id) {
    return jpaRepository.findById(id).map(VehicleRepositoryAdapter::toDomain);
  }

  @Override
  public List<Vehicle> findAll() {
    return jpaRepository.findAll().stream().map(VehicleRepositoryAdapter::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpaRepository.deleteById(id);
  }

  @Override
  public boolean existsById(UUID id) {
    return jpaRepository.existsById(id);
  }

  @Override
  public boolean existsByName(String name) {
    return jpaRepository.existsByName(name);
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return jpaRepository.existsByNameAndIdNot(name, excludedId);
  }

  private static Vehicle toDomain(VehicleJpaEntity entity) {
    return Vehicle.reconstitute(entity.getId(), entity.getName());
  }
}
