package com.chm.myfinances.application.paymentmethod;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating or renaming a {@code PaymentMethod} to a name another payment method already
 * has (exact match, case-sensitive - matches Postgres's own default {@code UNIQUE} semantics). Maps
 * to 409, same {@code @ResponseStatus} pattern as {@code CategoryNameAlreadyExistsException}.
 *
 * <p>Backed by {@code payment_methods.name UNIQUE} ({@code V10__db_constraint_hardening.sql}) as
 * defense in depth.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PaymentMethodNameAlreadyExistsException extends RuntimeException {

  public PaymentMethodNameAlreadyExistsException(String name) {
    super("A payment method named '" + name + "' already exists");
  }
}
