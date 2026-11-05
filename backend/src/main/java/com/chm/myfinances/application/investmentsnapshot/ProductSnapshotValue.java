package com.chm.myfinances.application.investmentsnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A product's value as of a date (F039): the sum of the latest snapshots of its holdings, and the
 * most recent date among those snapshots ({@code LatestInvestmentSnapshotQuery#valueByProduct}).
 */
public record ProductSnapshotValue(BigDecimal value, LocalDate latestSnapshotDate) {}
