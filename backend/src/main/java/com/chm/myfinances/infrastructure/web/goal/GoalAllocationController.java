package com.chm.myfinances.infrastructure.web.goal;

import com.chm.myfinances.application.goal.AllocationInput;
import com.chm.myfinances.application.goal.GoalService;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for a goal's allocations (F039 spec, ADR 0034). The whole set is replaced in one call:
 * the editor shows the full split, and a per-row endpoint would need cross-request sum checks. The
 * class maps {@code /api} because the product-side read ({@code GET
 * /api/investment-products/{id}/goal-allocations}) lives beside it.
 */
@RestController
@RequestMapping("/api")
public class GoalAllocationController {

  private final GoalService goalService;

  public GoalAllocationController(GoalService goalService) {
    this.goalService = goalService;
  }

  @PutMapping("/goals/{id}/allocations")
  @ApiResponse(
      responseCode = "200",
      description = "The stored allocations.",
      content =
          @Content(
              array =
                  @ArraySchema(schema = @Schema(implementation = GoalAllocationResponse.class))))
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:invalid-goal (a repeated product or an invalid"
              + " percentage), urn:my-finances:error:method-argument-not-valid, carrying an"
              + " errors[] array of {field, code} with code one of: not-null, positive,"
              + " decimal-max, digits",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:goal-not-found,"
              + " urn:my-finances:error:investment-product-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:goal-archived,"
              + " urn:my-finances:error:goal-allocation-exceeds-product",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<GoalAllocationResponse> replace(
      @PathVariable UUID id, @Valid @RequestBody ReplaceAllocationsRequest request) {
    List<AllocationInput> inputs =
        request.allocations().stream()
            .map(a -> new AllocationInput(a.productId(), a.percentage()))
            .toList();
    return goalService.replaceAllocations(id, inputs).stream()
        .map(GoalAllocationResponse::from)
        .toList();
  }
}
