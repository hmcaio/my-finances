package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.util.List;

/**
 * A goal's current-pace line (F041 spec, ADR 0035): a straight line from the goal's value now,
 * rising {@code monthlyPace} a month (the average monthly increase of its recorded value over the
 * last 12 months of history; it may be zero or negative). It is computed from recorded values, so
 * it is nominal money, unlike the scenarios. {@code points} start at the current month and run as
 * far as the scenarios do. The target fields follow a scenario's contract: a dated goal reports
 * {@code projectedValueAtTargetDate} and {@code shortfallAtTargetDate} (negative means a surplus),
 * a dateless goal reports {@code monthsToTarget} ({@code 0} when already reached, {@code null} when
 * the pace never gets there within 50 years); the other fields are {@code null}.
 */
public record PaceProjection(
    BigDecimal monthlyPace,
    List<ProjectionPoint> points,
    BigDecimal projectedValueAtTargetDate,
    BigDecimal shortfallAtTargetDate,
    Integer monthsToTarget) {}
