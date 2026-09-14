package com.chm.myfinances.infrastructure.web.paymentmethod;

import jakarta.validation.constraints.NotBlank;

/** Request body for {@code POST /api/payment-methods}. */
public record CreatePaymentMethodRequest(@NotBlank String name) {}
