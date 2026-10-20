package com.chm.myfinances.application.vehicle;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming a {@code Vehicle} to a name another vehicle already has (exact
 * match, case-sensitive). Maps to 409, same {@code @ResponseStatus} pattern as {@code
 * PaymentMethodNameAlreadyExistsException}. Backed by {@code vehicles.name UNIQUE} ({@code V18}) as
 * defense in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class VehicleNameAlreadyExistsException extends RuntimeException {

  public VehicleNameAlreadyExistsException(String name) {
    super("A vehicle named '" + name + "' already exists");
  }
}
