package com.chm.myfinances.application.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;

/**
 * Result of {@link InvestmentSnapshotService#record}: the stored snapshot and whether it was newly
 * created ({@code true}) or replaced the same-day snapshot ({@code false}) - the REST layer maps
 * that to {@code 201} vs {@code 200}.
 */
public record RecordedSnapshot(InvestmentSnapshot snapshot, boolean created) {}
