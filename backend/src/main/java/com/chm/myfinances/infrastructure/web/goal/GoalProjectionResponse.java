package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalProjection;
import com.chm.myfinances.application.goal.ProjectionPoint;
import com.chm.myfinances.application.goal.ProjectionScenario;
import com.chm.myfinances.application.goal.ScenarioProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * API representation of a {@link GoalProjection} (F039 spec, ADR 0034). Every amount is in today's
 * money. {@code month} is a {@code yyyy-MM} string on the wire (see {@code OpenApiConfig}). For a
 * dated goal {@code projectedValueAtTargetDate}, {@code shortfallAtTargetDate} (negative = surplus)
 * and {@code requiredMonthlyContribution} are set and {@code monthsToTarget} is null; for a
 * dateless goal it is the other way round ({@code monthsToTarget} null means "not reached within 50
 * years").
 */
public record GoalProjectionResponse(
    BigDecimal allocatedValue,
    LocalDate latestSnapshotDate,
    List<ScenarioProjectionResponse> scenarios) {

  /** One scenario of the projection. */
  public record ScenarioProjectionResponse(
      ProjectionScenario scenario,
      BigDecimal annualReturn,
      List<PointResponse> points,
      BigDecimal projectedValueAtTargetDate,
      BigDecimal shortfallAtTargetDate,
      BigDecimal requiredMonthlyContribution,
      Integer monthsToTarget) {

    static ScenarioProjectionResponse from(ScenarioProjection scenario) {
      return new ScenarioProjectionResponse(
          scenario.scenario(),
          scenario.annualReturn(),
          scenario.points().stream().map(PointResponse::from).toList(),
          scenario.projectedValueAtTargetDate(),
          scenario.shortfallAtTargetDate(),
          scenario.requiredMonthlyContribution(),
          scenario.monthsToTarget());
    }
  }

  /** One month of a series. */
  public record PointResponse(YearMonth month, BigDecimal value) {

    static PointResponse from(ProjectionPoint point) {
      return new PointResponse(point.month(), point.value());
    }
  }

  public static GoalProjectionResponse from(GoalProjection projection) {
    return new GoalProjectionResponse(
        projection.allocatedValue(),
        projection.latestSnapshotDate(),
        projection.scenarios().stream().map(ScenarioProjectionResponse::from).toList());
  }
}
