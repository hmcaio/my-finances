package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A goal's projection (F039 spec, ADR 0034), computed on read and never stored. {@code
 * allocatedValue} is the goal's current value (its share of each allocated product's latest
 * snapshot value); {@code latestSnapshotDate} is the most recent snapshot behind it, {@code null}
 * when nothing is allocated or no snapshot exists. The scenarios are in today's money. {@code pace}
 * (F041, ADR 0035) is the current-pace line drawn from recorded history, in nominal money, and
 * {@code null} when the goal has too little history for one; it is deliberately not a fourth entry
 * of {@code scenarios}.
 */
public record GoalProjection(
    BigDecimal allocatedValue,
    LocalDate latestSnapshotDate,
    List<ScenarioProjection> scenarios,
    PaceProjection pace) {}
