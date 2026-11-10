package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalProjection;
import com.chm.myfinances.application.goal.PaceProjection;
import com.chm.myfinances.application.goal.ProjectionScenario;
import com.chm.myfinances.application.goal.ScenarioProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * API representation of a {@link GoalProjection} (F039 spec, ADR 0034). Every amount is in today's
 * money. {@code month} is a {@code yyyy-MM} string on the wire (see {@code OpenApiConfig}). For a
 * dated goal {@code projectedValueAtTargetDate}, {@code shortfallAtTargetDate} (negative = surplus)
 * and {@code requiredMonthlyContribution} are set and {@code monthsToTarget} is null; for a
 * dateless goal it is the other way round ({@code monthsToTarget} null means "not reached within 50
 * years").
 *
 * <p>{@code pace} (F041, ADR 0035) is the current-pace line drawn from the goal's recorded history,
 * in nominal money, with the same target fields as a scenario; it is {@code null} when there is too
 * little history for one.
 */
public record GoalProjectionResponse(
    BigDecimal allocatedValue,
    LocalDate latestSnapshotDate,
    List<ScenarioProjectionResponse> scenarios,
    PaceProjectionResponse pace) {

  /** The current-pace line: the average monthly increase of the last 12 months, projected. */
  public record PaceProjectionResponse(
      BigDecimal monthlyPace,
      List<GoalPointResponse> points,
      BigDecimal projectedValueAtTargetDate,
      BigDecimal shortfallAtTargetDate,
      Integer monthsToTarget) {

    static PaceProjectionResponse from(PaceProjection pace) {
      return new PaceProjectionResponse(
          pace.monthlyPace(),
          pace.points().stream().map(GoalPointResponse::from).toList(),
          pace.projectedValueAtTargetDate(),
          pace.shortfallAtTargetDate(),
          pace.monthsToTarget());
    }
  }

  /** One scenario of the projection. */
  public record ScenarioProjectionResponse(
      ProjectionScenario scenario,
      BigDecimal annualReturn,
      List<GoalPointResponse> points,
      BigDecimal projectedValueAtTargetDate,
      BigDecimal shortfallAtTargetDate,
      BigDecimal requiredMonthlyContribution,
      Integer monthsToTarget) {

    static ScenarioProjectionResponse from(ScenarioProjection scenario) {
      return new ScenarioProjectionResponse(
          scenario.scenario(),
          scenario.annualReturn(),
          scenario.points().stream().map(GoalPointResponse::from).toList(),
          scenario.projectedValueAtTargetDate(),
          scenario.shortfallAtTargetDate(),
          scenario.requiredMonthlyContribution(),
          scenario.monthsToTarget());
    }
  }

  public static GoalProjectionResponse from(GoalProjection projection) {
    return new GoalProjectionResponse(
        projection.allocatedValue(),
        projection.latestSnapshotDate(),
        projection.scenarios().stream().map(ScenarioProjectionResponse::from).toList(),
        projection.pace() == null ? null : PaceProjectionResponse.from(projection.pace()));
  }
}
