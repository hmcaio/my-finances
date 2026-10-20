package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import java.util.UUID;

/** API representation of a {@link PaymentMethod}. */
public record PaymentMethodResponse(UUID id, String name) {

  public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
    return new PaymentMethodResponse(paymentMethod.getId(), paymentMethod.getName());
  }
}
