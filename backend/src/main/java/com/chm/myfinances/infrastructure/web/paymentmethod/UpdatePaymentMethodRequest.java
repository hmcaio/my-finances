package com.chm.myfinances.infrastructure.web.paymentmethod;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code PATCH /api/payment-methods/{id}}. */
public record UpdatePaymentMethodRequest(@NotBlank String name) {}
