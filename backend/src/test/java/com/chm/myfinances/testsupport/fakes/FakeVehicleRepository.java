package com.chm.myfinances.testsupport.fakes;

import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import java.util.UUID;

/**
 * In-memory test double for {@link VehicleRepository}, shared across application-service tests
 * (same spirit as {@link FakeIdGenerator}).
 */
public final class FakeVehicleRepository extends InMemoryRepository<Vehicle>
    implements VehicleRepository {

  public FakeVehicleRepository() {
    super(Vehicle::getId);
  }

  @Override
  public boolean existsByName(String name) {
    return values().stream().anyMatch(v -> v.getName().equals(name));
  }

  @Override
  public boolean existsByNameAndIdNot(String name, UUID excludedId) {
    return values().stream()
        .anyMatch(v -> v.getName().equals(name) && !v.getId().equals(excludedId));
  }
}
