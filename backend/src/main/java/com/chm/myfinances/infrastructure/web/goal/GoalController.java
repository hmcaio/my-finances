package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.GoalService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code Goal} (F039 spec, ADR 0034). Updates are {@code PATCH} with a full-replace
 * body, like every other update endpoint (docs/conventions/backend-structure.md). The allocations
 * of a goal are {@code GoalAllocationController}; projection, history, the dashboard and the
 * emergency-fund suggestion are {@code GoalReportController}.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

  private static final String VALIDATION_CODES =
      "urn:my-finances:error:method-argument-not-valid, carrying an errors[] array of {field, code}"
          + " with code one of: not-blank, size, not-null, positive, positive-or-zero, decimal-min,"
          + " decimal-max, digits";

  private final GoalService goalService;

  public GoalController(GoalService goalService) {
    this.goalService = goalService;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "The goals, non-archived unless includeArchived=true.",
      content =
          @Content(array = @ArraySchema(schema = @Schema(implementation = GoalResponse.class))))
  public List<GoalResponse> list(
      @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
    return goalService.findAll(includeArchived).stream().map(GoalResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of "
              + VALIDATION_CODES
              + "; or urn:my-finances:error:invalid-goal (a domain violation or a target date not"
              + " after today)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalResponse create(@Valid @RequestBody CreateGoalRequest request) {
    return GoalResponse.from(goalService.create(request.toInput()));
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "200",
      description = "The goal.",
      content = @Content(schema = @Schema(implementation = GoalResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalResponse get(@PathVariable UUID id) {
    return GoalResponse.from(goalService.findById(id));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "200",
      description = "The edited goal.",
      content = @Content(schema = @Schema(implementation = GoalResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of "
              + VALIDATION_CODES
              + "; or urn:my-finances:error:invalid-goal (a domain violation or a changed target"
              + " date not after today)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: urn:my-finances:error:goal-allocation-exceeds-product (restoring an archived goal"
              + " whose percentages no longer fit)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateGoalRequest request) {
    return GoalResponse.from(goalService.edit(id, request.toInput(), request.status()));
  }

  @PatchMapping("/{id}/show-on-dashboard")
  @ApiResponse(
      responseCode = "200",
      description = "The goal with the flag applied.",
      content = @Content(schema = @Schema(implementation = GoalResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "type: urn:my-finances:error:method-argument-not-valid (not-null)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: urn:my-finances:error:goal-archived (turning it on for an archived goal)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public GoalResponse setShowOnDashboard(
      @PathVariable UUID id, @Valid @RequestBody ShowOnDashboardRequest request) {
    return GoalResponse.from(goalService.setShowOnDashboard(id, request.showOnDashboard()));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:goal-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    goalService.delete(id);
  }
}
