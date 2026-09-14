package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.domain.shared.NameConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/payment-methods/{id}}. */
public record UpdatePaymentMethodRequest(
    @NotBlank @Size(max = NameConstraints.MAX_NAME_LENGTH) String name) {}
