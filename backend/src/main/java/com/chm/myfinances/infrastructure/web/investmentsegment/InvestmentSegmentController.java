package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.application.investmentsegment.InvestmentSegmentService;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST API for {@code InvestmentSegment} (F026 spec). */
@RestController
@RequestMapping("/api/investment-segments")
public class InvestmentSegmentController {

  private final InvestmentSegmentService segmentService;

  public InvestmentSegmentController(InvestmentSegmentService segmentService) {
    this.segmentService = segmentService;
  }

  @GetMapping
  public List<InvestmentSegmentResponse> list() {
    return segmentService.findAll().stream().map(InvestmentSegmentResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-segment-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentSegmentResponse create(
      @Valid @RequestBody CreateInvestmentSegmentRequest request) {
    InvestmentSegment segment = segmentService.create(request.name());
    return InvestmentSegmentResponse.from(segment);
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-segment-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-segment-name-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentSegmentResponse rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentSegmentRequest request) {
    InvestmentSegment segment = segmentService.rename(id, request.name());
    return InvestmentSegmentResponse.from(segment);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-segment-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-segment-in-use",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    segmentService.delete(id);
  }
}
