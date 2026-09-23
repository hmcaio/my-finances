package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code POST /api/investment-products/{id}/snapshots} (F009 spec). {@code
 * balance} is {@code >= 0}: {@code 0} is the legitimate value of a liquidated position.
 */
public record RecordSnapshotRequest(
    @NotNull LocalDate date,
    @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balance) {}
