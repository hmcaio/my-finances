package com.chm.myfinances.application.investmentsnapshot;

/**
 * Result of {@link InvestmentSnapshotService#recordBatch}: how many snapshots the batch created and
 * how many replaced a same-day snapshot (an unchanged same-day balance counts as replaced).
 */
public record RecordedBatch(int created, int replaced) {}
