package com.chm.myfinances.application.vehicle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Vehicle} id doesn't resolve to an existing vehicle. Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class VehicleNotFoundException extends RuntimeException {

  public VehicleNotFoundException(UUID id) {
    super("Vehicle not found: " + id);
  }
}
