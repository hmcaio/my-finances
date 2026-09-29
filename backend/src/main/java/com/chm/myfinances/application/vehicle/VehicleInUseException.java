package com.chm.myfinances.application.vehicle;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a {@code Vehicle} that's referenced by at least one fuel {@code Transaction}
 * (F024 spec). Maps to 409, same {@code @ResponseStatus} pattern as {@code
 * PaymentMethodInUseException}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class VehicleInUseException extends RuntimeException {

  public VehicleInUseException(UUID id) {
    super("Vehicle is referenced by at least one fuel transaction and cannot be deleted: " + id);
  }
}
