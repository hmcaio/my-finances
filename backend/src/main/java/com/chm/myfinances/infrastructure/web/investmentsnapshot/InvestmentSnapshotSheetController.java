package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotService;
import com.chm.myfinances.application.investmentsnapshot.OpenHoldingSnapshotSheetQuery;
import com.chm.myfinances.application.investmentsnapshot.SnapshotBatchEntry;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API of the investment snapshot sheet (F040): {@code GET} lists every open holding with its
 * latest snapshot, {@code POST} records one snapshot per entry atomically, on one date.
 */
@RestController
@RequestMapping("/api/investment-snapshot-sheet")
public class InvestmentSnapshotSheetController {

  private final OpenHoldingSnapshotSheetQuery sheetQuery;
  private final InvestmentSnapshotService snapshotService;

  public InvestmentSnapshotSheetController(
      OpenHoldingSnapshotSheetQuery sheetQuery, InvestmentSnapshotService snapshotService) {
    this.sheetQuery = sheetQuery;
    this.snapshotService = snapshotService;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description =
          "Every open holding with its latest snapshot, ordered by account name then product name.",
      content =
          @Content(
              array =
                  @ArraySchema(schema = @Schema(implementation = SnapshotSheetRowResponse.class))))
  public List<SnapshotSheetRowResponse> list() {
    return sheetQuery.openHoldings().stream().map(SnapshotSheetRowResponse::from).toList();
  }

  /** All or none: any failure leaves no snapshot and no audit entry behind. */
  @PostMapping
  @ApiResponse(
      responseCode = "200",
      description = "How many snapshots were created and how many replaced a same-day one.",
      content = @Content(schema = @Schema(implementation = RecordSnapshotSheetResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-not-valid, carrying an errors[] "
              + "array of {field, code} with code one of: not-null, not-empty, size, "
              + "positive-or-zero, digits, holding-ids-unique (field null); row fields are "
              + "entries[i].holdingId and entries[i].balance",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: urn:my-finances:error:investment-snapshot-sheet-holdings-not-found, carrying an "
              + "errors[] array with one {field: entries[i].holdingId, code: "
              + "investment-holding-not-found} per missing holding",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-snapshot-date-taken",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public RecordSnapshotSheetResponse record(
      @Valid @RequestBody RecordSnapshotSheetRequest request) {
    List<SnapshotBatchEntry> entries =
        request.entries().stream()
            .map(entry -> new SnapshotBatchEntry(entry.holdingId(), entry.balance()))
            .toList();
    return RecordSnapshotSheetResponse.from(snapshotService.recordBatch(request.date(), entries));
  }
}
