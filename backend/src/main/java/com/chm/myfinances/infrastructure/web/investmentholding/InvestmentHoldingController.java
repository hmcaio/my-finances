package com.chm.myfinances.infrastructure.web.investmentholding;

import com.chm.myfinances.application.investmentholding.InvestmentHoldingService;
import com.chm.myfinances.application.investmentsnapshot.InvestmentSnapshotFreshnessQuery;
import com.chm.myfinances.application.investmentsnapshot.LatestInvestmentSnapshotQuery;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
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
 * REST API for {@code InvestmentHolding} (F022 spec, ADR 0020): the many-to-many link between a
 * product and the {@code INVESTMENT} account it's held in. {@code GET} is filtered by exactly one
 * of {@code ?productId=}/{@code ?accountId=} (400 if neither or both are given).
 */
@RestController
@RequestMapping("/api/investment-holdings")
public class InvestmentHoldingController {

  private final InvestmentHoldingService holdingService;
  private final LatestInvestmentSnapshotQuery latestSnapshotQuery;
  private final InvestmentSnapshotFreshnessQuery freshnessQuery;
  private final Clock clock;

  public InvestmentHoldingController(
      InvestmentHoldingService holdingService,
      LatestInvestmentSnapshotQuery latestSnapshotQuery,
      InvestmentSnapshotFreshnessQuery freshnessQuery,
      Clock clock) {
    this.holdingService = holdingService;
    this.latestSnapshotQuery = latestSnapshotQuery;
    this.freshnessQuery = freshnessQuery;
    this.clock = clock;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "400",
      description = "type: urn:my-finances:error:investment-holding-filter-required",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-product-not-found, "
              + "urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<InvestmentHoldingResponse> list(
      @RequestParam(name = "productId", required = false) UUID productId,
      @RequestParam(name = "accountId", required = false) UUID accountId) {
    if ((productId == null) == (accountId == null)) {
      throw new InvestmentHoldingFilterRequiredException();
    }
    List<InvestmentHolding> holdings =
        productId != null
            ? holdingService.findByProduct(productId)
            : holdingService.findByAccount(accountId);
    return holdings.stream().map(this::toResponse).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:investment-product-not-found, "
              + "urn:my-finances:error:account-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-account-required, "
              + "urn:my-finances:error:investment-holding-already-exists",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentHoldingResponse create(
      @Valid @RequestBody CreateInvestmentHoldingRequest request) {
    return toResponse(
        holdingService.create(request.productId(), request.accountId(), request.additionalNotes()));
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentHoldingResponse get(@PathVariable UUID id) {
    return toResponse(holdingService.findById(id));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentHoldingResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateInvestmentHoldingRequest request) {
    return toResponse(holdingService.editNotes(id, request.additionalNotes()));
  }

  /**
   * {@code 409} while the latest snapshot is non-zero (F009/F022): record a zero snapshot first.
   */
  @PostMapping("/{id}/close")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:investment-holding-already-closed, "
              + "urn:my-finances:error:investment-holding-not-empty",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public InvestmentHoldingResponse close(@PathVariable UUID id) {
    return toResponse(holdingService.close(id));
  }

  /**
   * {@code 409} when the holding has history (a snapshot or a tagged transfer, F009/F022): the user
   * closes it instead.
   */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description = "type: urn:my-finances:error:investment-holding-has-history",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    holdingService.delete(id);
  }

  private InvestmentHoldingResponse toResponse(InvestmentHolding holding) {
    return InvestmentHoldingResponse.from(
        holding,
        holdingService.hasHistory(holding.getId()),
        freshnessQuery.needsSnapshot(holding.getId(), LocalDate.now(clock)),
        latestSnapshotQuery.latestOf(holding.getId()).orElse(null));
  }
}
