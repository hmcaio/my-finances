package com.chm.myfinances.application.goal;

import java.math.BigDecimal;

/**
 * Where a goal stands now (F039 spec, ADR 0034), from its allocated value and the base scenario.
 * {@code progressPercent} is {@code allocatedValue / target x 100} (it may exceed 100) and {@code
 * targetReached} is the automatic indicator {@code allocatedValue >= target} (it never changes the
 * goal's status). For a dated goal {@code projectedShortfall} is the base scenario's shortfall at
 * the target date ({@code <= 0} means on track) and {@code monthsToTarget} is {@code null}; for a
 * dateless goal {@code projectedShortfall} is {@code null} and {@code monthsToTarget} is the base
 * scenario's (null = not within 50 years).
 */
public record GoalProgress(
    BigDecimal allocatedValue,
    BigDecimal progressPercent,
    boolean targetReached,
    BigDecimal projectedShortfall,
    Integer monthsToTarget) {}
