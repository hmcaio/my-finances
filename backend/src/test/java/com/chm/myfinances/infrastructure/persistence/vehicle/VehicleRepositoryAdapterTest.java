package com.chm.myfinances.infrastructure.persistence.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.vehicle.Vehicle;
import com.chm.myfinances.domain.vehicle.VehicleRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link VehicleRepositoryAdapter}, against a real ephemeral
 * Postgres via Testcontainers (ADR 0010). Mirrors {@code PaymentMethodRepositoryAdapterTest}.
 */
@DatabaseIntegrationTest
class VehicleRepositoryAdapterTest {

  @Autowired private VehicleRepository vehicleRepository;

  @Test
  void savesAndReloadsAVehicle() {
    Vehicle vehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Civic Test"));

    Optional<Vehicle> reloaded = vehicleRepository.findById(vehicle.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Civic Test");
  }

  @Test
  void renamePersists() {
    Vehicle vehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Original Test"));

    vehicle.rename("Renamed Test");
    vehicleRepository.save(vehicle);

    Optional<Vehicle> reloaded = vehicleRepository.findById(vehicle.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed Test");
  }

  @Test
  void deleteRemovesTheVehicle() {
    Vehicle vehicle = vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Temp Test"));

    vehicleRepository.deleteById(vehicle.getId());

    assertThat(vehicleRepository.existsById(vehicle.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Unique Name Test"));

    assertThat(vehicleRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(vehicleRepository.existsByName("unique name test")).isFalse();
    assertThat(vehicleRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Vehicle vehicle =
        vehicleRepository.save(Vehicle.create(UUID.randomUUID(), "Exclude Self Test"));

    assertThat(vehicleRepository.existsByNameAndIdNot("Exclude Self Test", vehicle.getId()))
        .isFalse();
    assertThat(vehicleRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }
}
