package com.chm.myfinances.application.goal;

import java.math.BigDecimal;
import java.util.List;

/**
 * One scenario of a {@link GoalProjection} (F039 spec, ADR 0034). {@code points} start at the
 * current month (the allocated value now) and run monthly.
 *
 * <p>For a dated goal: {@code projectedValueAtTargetDate} is the last point's value, {@code
 * shortfallAtTargetDate} is {@code target - projected} (negative means a surplus), {@code
 * requiredMonthlyContribution} is the contribution that reaches the target on time (zero when the
 * allocated value alone is enough; {@code null} when the target month is already here and the
 * target is not met, since no contribution can help), and {@code monthsToTarget} is {@code null}.
 * For a dateless goal those first three are {@code null} and {@code monthsToTarget} is the first
 * month the value reaches the target ({@code 0} if it already has), or {@code null} when it does
 * not within 50 years.
 */
public record ScenarioProjection(
    ProjectionScenario scenario,
    BigDecimal annualReturn,
    List<ProjectionPoint> points,
    BigDecimal projectedValueAtTargetDate,
    BigDecimal shortfallAtTargetDate,
    BigDecimal requiredMonthlyContribution,
    Integer monthsToTarget) {}
