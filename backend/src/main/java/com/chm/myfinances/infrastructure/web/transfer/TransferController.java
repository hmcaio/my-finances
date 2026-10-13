package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.application.transfer.TransferService;
import com.chm.myfinances.domain.transfer.TradeConfirmationLine;
import com.chm.myfinances.domain.transfer.Transfer;
import com.chm.myfinances.domain.transfer.TransferFilter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
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
 * REST API for {@code Transfer} (F005 spec). F027 (ADR 0024): {@code create}/{@code edit} first
 * check the create/edit mutual-exclusivity rule ({@code
 * TransferService.requireExactlyOneRequestShape}), then dispatch to whichever of {@code
 * TransferService}'s create/edit overloads matches - never through a shared self-invoking wrapper,
 * so each overload's own {@code @Transactional} boundary is honored (backend {@code CLAUDE.md},
 * "Transactions": a self-call bypasses the Spring proxy).
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

  private final TransferService transferService;

  public TransferController(TransferService transferService) {
    this.transferService = transferService;
  }

  /**
   * Filtered, paginated list - {@code GET /api/transfers?dateFrom=&dateTo=&accountId=&
   * investmentProductId=&page=&size=&sort=}. {@code accountId} matches either side of the transfer
   * (PRD S6.9); {@code investmentProductId} (F009, now a join to {@code transfer_trade_lines},
   * F027) is one product's buy/sell history, returning the owning confirmations. Defaults to 20 per
   * page, most recent first ({@code date} descending) - same {@link PagedModel} envelope convention
   * as F004's transaction list endpoint.
   */
  @GetMapping
  public PagedModel<TransferResponse> list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID investmentProductId,
      @PageableDefault(size = 20, sort = "date", direction = Sort.Direction.DESC)
          Pageable pageable) {
    TransferFilter filter = new TransferFilter(dateFrom, dateTo, accountId, investmentProductId);
    Page<Transfer> page = transferService.findAll(filter, pageable);
    return new PagedModel<>(page.map(TransferResponse::from));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:same-account-transfer, "
              + "urn:my-finances:error:invalid-trade-confirmation. May instead be "
              + "urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive, not-blank, size, "
              + "positive-or-zero, digits, not-empty, "
              + "at-most-one-resulting-balance-per-product, close-holding-only-on-sell",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:account-not-found, "
              + "urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:account-closed, "
              + "urn:my-finances:error:investment-transfer-invalid, "
              + "urn:my-finances:error:investment-holding-closed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransferResponse create(@Valid @RequestBody CreateTransferRequest request) {
    List<TradeConfirmationLine> lines = toDomainLines(request.tradeConfirmation());
    TransferService.requireExactlyOneRequestShape(
        request.fromAccountId(),
        request.toAccountId(),
        request.amount(),
        request.cashAccountId(),
        request.investmentAccountId(),
        lines);
    Transfer transfer =
        lines != null
            ? transferService.createTradeConfirmation(
                request.date(),
                request.cashAccountId(),
                request.investmentAccountId(),
                request.description(),
                request.additionalNotes(),
                request.tradeConfirmation().taxes(),
                lines)
            : transferService.create(
                request.date(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.description(),
                request.additionalNotes());
    return TransferResponse.from(transfer);
  }

  @GetMapping("/{id}")
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:transfer-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransferResponse get(@PathVariable UUID id) {
    return TransferResponse.from(transferService.findById(id));
  }

  @PatchMapping("/{id}")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: one of urn:my-finances:error:same-account-transfer, "
              + "urn:my-finances:error:invalid-trade-confirmation. May instead be "
              + "urn:my-finances:error:method-argument-not-valid (F032), carrying an errors[] "
              + "array of {field, code} with code one of: not-null, positive, not-blank, size, "
              + "positive-or-zero, digits, not-empty, "
              + "at-most-one-resulting-balance-per-product, close-holding-only-on-sell",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "404",
      description =
          "type: one of urn:my-finances:error:transfer-not-found, "
              + "urn:my-finances:error:account-not-found, "
              + "urn:my-finances:error:investment-holding-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @ApiResponse(
      responseCode = "409",
      description =
          "type: one of urn:my-finances:error:account-closed, "
              + "urn:my-finances:error:investment-transfer-invalid, "
              + "urn:my-finances:error:investment-holding-closed",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public TransferResponse edit(
      @PathVariable UUID id, @Valid @RequestBody UpdateTransferRequest request) {
    List<TradeConfirmationLine> lines = toDomainLines(request.tradeConfirmation());
    TransferService.requireExactlyOneRequestShape(
        request.fromAccountId(),
        request.toAccountId(),
        request.amount(),
        request.cashAccountId(),
        request.investmentAccountId(),
        lines);
    Transfer transfer =
        lines != null
            ? transferService.editTradeConfirmation(
                id,
                request.date(),
                request.cashAccountId(),
                request.investmentAccountId(),
                request.description(),
                request.additionalNotes(),
                request.tradeConfirmation().taxes(),
                lines)
            : transferService.edit(
                id,
                request.date(),
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                request.description(),
                request.additionalNotes());
    return TransferResponse.from(transfer);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:transfer-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public void delete(@PathVariable UUID id) {
    transferService.delete(id);
  }

  private static List<TradeConfirmationLine> toDomainLines(TradeConfirmationRequest request) {
    if (request == null) {
      return null;
    }
    return request.lines().stream()
        .map(
            l ->
                new TradeConfirmationLine(
                    l.productId(),
                    l.side(),
                    l.quantity(),
                    l.unitPrice(),
                    l.resultingBalance(),
                    l.closeHoldingOrDefault()))
        .toList();
  }
}
