package com.chm.myfinances.application.paymentmethod;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a {@code PaymentMethod} id doesn't resolve to an existing payment method. Maps to
 * 404.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class PaymentMethodNotFoundException extends RuntimeException {

  public PaymentMethodNotFoundException(UUID id) {
    super("Payment method not found: " + id);
  }
}
