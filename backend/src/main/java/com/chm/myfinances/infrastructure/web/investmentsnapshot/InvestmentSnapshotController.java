package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.application.investmentsnapshot.RecordedSnapshot;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentSnapshot} (F009 spec), nested under the holding (moved from the
 * product by F022/ADR 0020). A plain list (a snapshot per holding per manual entry, most recent
 * first).
 */
@RestController
@RequestMapping("/api/investment-holdings/{holdingId}/snapshots")
public class InvestmentSnapshotController {

  private final InvestmentSnapshotService snapshotService;

  public InvestmentSnapshotController(InvestmentSnapshotService snapshotService) {
    this.snapshotService = snapshotService;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "The holding's snapshots, most recent date first.",
      content =
          @Content(
              array =
                  @ArraySchema(
                      schema = @Schema(implementation = InvestmentSnapshotResponse.class))))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<InvestmentSnapshotResponse> list(@PathVariable UUID holdingId) {
    return snapshotService.findByHolding(holdingId).stream()
        .map(InvestmentSnapshotResponse::from)
        .toList();
  }

  /** {@code 201} when a snapshot was created, {@code 200} when it replaced the same-day one. */
  @PostMapping
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive-or-zero, digits",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-snapshot-date-taken",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public ResponseEntity<InvestmentSnapshotResponse> record(
      @PathVariable UUID holdingId, @Valid @RequestBody RecordSnapshotRequest request) {
    RecordedSnapshot recorded =
        snapshotService.record(holdingId, request.date(), request.balance());
    return ResponseEntity.status(recorded.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(InvestmentSnapshotResponse.from(recorded.snapshot()));
  }

  @PutMapping("/{snapshotId}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive-or-zero, digits",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-holding-not-found, "
              + "urn:my-finances:error:investment-snapshot-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-snapshot-date-taken, "
              + "urn:my-finances:error:investment-snapshot-closed-holding",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentSnapshotResponse update(
      @PathVariable UUID holdingId,
      @PathVariable UUID snapshotId,
      @Valid @RequestBody UpdateSnapshotRequest request) {
    return InvestmentSnapshotResponse.from(
        snapshotService.update(holdingId, snapshotId, request.date(), request.balance()));
  }

  @DeleteMapping("/{snapshotId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-holding-not-found, "
              + "urn:my-finances:error:investment-snapshot-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-snapshot-closed-holding",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID holdingId, @PathVariable UUID snapshotId) {
    snapshotService.delete(holdingId, snapshotId);
  }
}
