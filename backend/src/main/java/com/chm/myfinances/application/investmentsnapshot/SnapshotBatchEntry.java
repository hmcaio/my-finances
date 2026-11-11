package com.chm.myfinances.application.investmentsnapshot;

import java.math.BigDecimal;
import java.util.UUID;

/** One row of {@link InvestmentSnapshotService#recordBatch}: a holding and its balance. */
public record SnapshotBatchEntry(UUID holdingId, BigDecimal balance) {}
