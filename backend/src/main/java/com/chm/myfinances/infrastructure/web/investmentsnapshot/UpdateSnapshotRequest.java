package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code PUT /api/investment-products/{id}/snapshots/{snapshotId}}: a full replace
 * of date and balance, validated like {@link RecordSnapshotRequest}.
 */
public record UpdateSnapshotRequest(
    @NotNull LocalDate date,
    @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balance) {}
