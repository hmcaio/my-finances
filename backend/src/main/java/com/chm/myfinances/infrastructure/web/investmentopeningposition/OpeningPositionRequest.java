package com.chm.myfinances.infrastructure.web.investmentopeningposition;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/investment-holdings/{holdingId}/opening-position}, and the
 * optional {@code openingPosition} of {@code POST /api/investment-products} (F038 spec, ADR 0033).
 * {@code quantity} and {@code unitPrice} are positive with up to 8 decimals (the {@code
 * numeric(19,8)} columns). {@code currentValue} is optional and is never stored on the position: it
 * records the holding's snapshot dated {@code asOfDate}, so it is {@code >= 0} like a snapshot
 * balance. The as-of date being today or earlier is checked in the service (it needs the clock).
 */
public record OpeningPositionRequest(
    @NotNull LocalDate asOfDate,
    @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal quantity,
    @NotNull @Positive @Digits(integer = 11, fraction = 8) BigDecimal unitPrice,
    @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal currentValue) {}
