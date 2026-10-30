package com.chm.myfinances.infrastructure.web.investmentopeningposition;

import com.chm.myfinances.application.investmentopeningposition.InvestmentOpeningPositionService;
import com.chm.myfinances.application.investmentopeningposition.RecordedOpeningPosition;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentOpeningPosition} (F038 spec, ADR 0033), nested under the holding it
 * belongs to. A holding has at most one position, so the holding is the key: there is no position
 * id in any URL, {@code PUT} creates or replaces, and a missing position is its own {@code 404}
 * problem type (distinct from an unknown holding) that the frontend maps to the empty state.
 */
@RestController
@RequestMapping("/api/investment-holdings/{holdingId}/opening-position")
public class InvestmentOpeningPositionController {

  private final InvestmentOpeningPositionService positionService;

  public InvestmentOpeningPositionController(InvestmentOpeningPositionService positionService) {
    this.positionService = positionService;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "The holding's opening position.",
      content = @Content(schema = @Schema(implementation = OpeningPositionResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-holding-not-found, "
              + "urn:my-finances:error:investment-opening-position-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public OpeningPositionResponse get(@PathVariable UUID holdingId) {
    return OpeningPositionResponse.from(positionService.findByHoldingId(holdingId));
  }

  /**
   * {@code 201} when the holding had no position, {@code 200} when this replaced its existing one.
   * A {@code currentValue} also records the holding's snapshot dated {@code asOfDate}.
   */
  @PutMapping
  @ApiResponse(
      responseCode = "200",
      description = "The holding's existing opening position was replaced.",
      content = @Content(schema = @Schema(implementation = OpeningPositionResponse.class)))
  @ApiResponse(
      responseCode = "201",
      description = "The opening position was created.",
      content = @Content(schema = @Schema(implementation = OpeningPositionResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive, positive-or-zero, "
              + "digits; or urn:my-finances:error:invalid-opening-position (a future asOfDate)",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-holding-closed, "
              + "urn:my-finances:error:opening-position-after-trades",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<OpeningPositionResponse> put(
      @PathVariable UUID holdingId, @Valid @RequestBody OpeningPositionRequest request) {
    RecordedOpeningPosition recorded =
        positionService.upsert(
            holdingId,
            request.asOfDate(),
            request.quantity(),
            request.unitPrice(),
            request.currentValue());
    return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(OpeningPositionResponse.from(recorded.position()));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-holding-not-found, "
              + "urn:my-finances:error:investment-opening-position-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-holding-closed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID holdingId) {
    positionService.delete(holdingId);
  }
}
