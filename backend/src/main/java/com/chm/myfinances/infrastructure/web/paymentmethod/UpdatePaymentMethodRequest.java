package com.chm.myfinances.infrastructure.web.paymentmethod;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request body for {@code PATCH /api/payment-methods/{id}}. */
public record UpdatePaymentMethodRequest(
    @NotBlank @Size(max = TextFieldConstraints.MAX_NAME_LENGTH) String name) {}
