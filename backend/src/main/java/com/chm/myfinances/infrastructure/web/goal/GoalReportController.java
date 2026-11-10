package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.EmergencyFundSuggestionQuery;
import com.chm.myfinances.application.goal.GoalContributionSuggestionQuery;
import com.chm.myfinances.application.goal.GoalDashboardQuery;
import com.chm.myfinances.application.goal.GoalHistoryQuery;
import com.chm.myfinances.application.goal.GoalProjectionQuery;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only goal computations (F039 spec, ADR 0034): the projection, the progress history, the
 * dashboard summaries, the emergency-fund suggestion and (F041) the contribution suggestion from
 * history. Computed on read, never stored. There is deliberately no class-level {@code @Validated}:
 * the parameter constraint below raises Spring's own {@code HandlerMethodValidationException} (a
 * 400, docs/conventions/errors.md).
 */
@RestController
@RequestMapping("/api/goals")
public class GoalReportController {

  /** Default window when {@code from} is omitted: the last twelve months up to {@code to}. */
  private static final int DEFAULT_HISTORY_MONTHS = 12;

  private final GoalProjectionQuery projectionQuery;
  private final GoalHistoryQuery historyQuery;
  private final GoalDashboardQuery dashboardQuery;
  private final EmergencyFundSuggestionQuery suggestionQuery;
  private final GoalContributionSuggestionQuery contributionSuggestionQuery;
  private final Clock clock;

  public GoalReportController(
      GoalProjectionQuery projectionQuery,
      GoalHistoryQuery historyQuery,
      GoalDashboardQuery dashboardQuery,
      EmergencyFundSuggestionQuery suggestionQuery,
      GoalContributionSuggestionQuery contributionSuggestionQuery,
      Clock clock) {
    this.projectionQuery = projectionQuery;
    this.historyQuery = historyQuery;
    this.dashboardQuery = dashboardQuery;
    this.suggestionQuery = suggestionQuery;
    this.contributionSuggestionQuery = contributionSuggestionQuery;
    this.clock = clock;
  }

  @GetMapping("/{id}/projection")
  @ApiResponse(
      responseCode = "200",
      description =
          "The pessimistic, base and optimistic projections, in today's money, and the current"
              + " pace drawn from history (null when there is too little of it).",
      content = @Content(schema = @Schema(implementation = GoalProjectionResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalProjectionResponse projection(@PathVariable UUID id) {
    return GoalProjectionResponse.from(projectionQuery.projection(id));
  }

  /**
   * {@code GET /api/goals/{id}/history?from=&to=}: {@code from}/{@code to} are {@code yyyy-MM};
   * {@code to} defaults to the current month and {@code from} to eleven months before {@code to}.
   * The current split of the goal applied to each product's value series.
   */
  @GetMapping("/{id}/history")
  @ApiResponse(
      responseCode = "200",
      description = "Monthly values of the goal as of its current allocation.",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = GoalPointResponse.class))))
  @ApiResponse(
      responseCode = "400",
      description = "type: urn:my-finances:error:invalid-value-series-range",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<GoalPointResponse> history(
      @PathVariable UUID id,
      @RequestParam(name = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth to) {
    YearMonth effectiveTo = to != null ? to : YearMonth.now(clock);
    YearMonth effectiveFrom =
        from != null ? from : effectiveTo.minusMonths(DEFAULT_HISTORY_MONTHS - 1);
    return historyQuery.history(id, effectiveFrom, effectiveTo).stream()
        .map(GoalPointResponse::from)
        .toList();
  }

  @GetMapping("/{id}/contribution-suggestion")
  @ApiResponse(
      responseCode = "200",
      description =
          "The average monthly amount put into the goal's products over the last 12 full months.",
      content = @Content(schema = @Schema(implementation = ContributionSuggestionResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ContributionSuggestionResponse contributionSuggestion(@PathVariable UUID id) {
    return ContributionSuggestionResponse.from(contributionSuggestionQuery.suggest(id));
  }

  @GetMapping("/dashboard")
  @ApiResponse(
      responseCode = "200",
      description = "The goals flagged to show on the dashboard, in the standard order.",
      content =
          @Content(
              array = @ArraySchema(schema = @Schema(implementation = GoalSummaryResponse.class))))
  public List<GoalSummaryResponse> dashboard() {
    return dashboardQuery.dashboard().stream().map(GoalSummaryResponse::from).toList();
  }

  @GetMapping("/emergency-fund-suggestion")
  @ApiResponse(
      responseCode = "200",
      description = "months x the average monthly expense of the last 12 full months.",
      content = @Content(schema = @Schema(implementation = EmergencyFundSuggestionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:handler-method-validation (months outside 1..120),"
              + " urn:my-finances:error:invalid-goal",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public EmergencyFundSuggestionResponse emergencyFundSuggestion(
      @RequestParam(name = "months", defaultValue = "6") @Min(1) @Max(120) int months) {
    return EmergencyFundSuggestionResponse.from(suggestionQuery.suggest(months));
  }
}
