package com.chm.myfinances.application.paymentmethod;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting a {@code PaymentMethod} that's referenced by at least one {@code
 * Transaction} (F002 plan.md's deferred delete guard, added now that F004's {@code transactions}
 * table exists to check against). Maps to 409, same {@code @ResponseStatus} pattern as {@code
 * CategoryInUseException}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class PaymentMethodInUseException extends RuntimeException {

  public PaymentMethodInUseException(UUID id) {
    super("Payment method is referenced by at least one transaction and cannot be deleted: " + id);
  }
}
