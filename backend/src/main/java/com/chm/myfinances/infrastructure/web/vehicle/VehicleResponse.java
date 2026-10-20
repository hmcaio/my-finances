package com.chm.myfinances.infrastructure.web.vehicle;

import com.chm.myfinances.domain.vehicle.Vehicle;
import java.util.UUID;

/** API representation of a {@link Vehicle}. */
public record VehicleResponse(UUID id, String name) {

  public static VehicleResponse from(Vehicle vehicle) {
    return new VehicleResponse(vehicle.getId(), vehicle.getName());
  }
}
