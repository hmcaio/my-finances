package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One row of {@link RecordSnapshotSheetRequest}: a holding and its balance on the sheet's date. The
 * balance rules are those of {@link RecordSnapshotRequest} ({@code 0} is a liquidated position).
 */
public record SnapshotSheetEntryRequest(
    @NotNull UUID holdingId,
    @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal balance) {}
