package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalProjectionQuery;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only goal computations (F039 spec, ADR 0034): the projection, the progress history, the
 * dashboard summaries and the emergency-fund suggestion. Computed on read, never stored.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalReportController {

  private final GoalProjectionQuery projectionQuery;

  public GoalReportController(GoalProjectionQuery projectionQuery) {
    this.projectionQuery = projectionQuery;
  }

  @GetMapping("/{id}/projection")
  @ApiResponse(
      responseCode = "200",
      description = "The pessimistic, base and optimistic projections, in today's money.",
      content = @Content(schema = @Schema(implementation = GoalProjectionResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalProjectionResponse projection(@PathVariable UUID id) {
    return GoalProjectionResponse.from(projectionQuery.projection(id));
  }
}
