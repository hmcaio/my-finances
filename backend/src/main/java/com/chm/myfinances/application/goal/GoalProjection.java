package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A goal's projection (F039 spec, ADR 0034), computed on read and never stored. {@code
 * allocatedValue} is the goal's current value (its share of each allocated product's latest
 * snapshot value); {@code latestSnapshotDate} is the most recent snapshot behind it, {@code null}
 * when nothing is allocated or no snapshot exists. Everything is in today's money.
 */
public record GoalProjection(
    BigDecimal allocatedValue, LocalDate latestSnapshotDate, List<ScenarioProjection> scenarios) {}
