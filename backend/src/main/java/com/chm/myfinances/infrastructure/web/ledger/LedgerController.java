package com.chm.myfinances.infrastructure.web.ledger;

import com.chm.myfinances.application.ledger.LedgerEntryType;
import com.chm.myfinances.application.ledger.LedgerFilter;
import com.chm.myfinances.application.ledger.LedgerQuery;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the Ledger (F037): one read endpoint that interleaves transactions and transfers,
 * newest first, with a summary of the whole filtered scope. Writes still go through the transaction
 * and transfer endpoints.
 */
@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

  private final LedgerQuery ledgerQuery;

  public LedgerController(LedgerQuery ledgerQuery) {
    this.ledgerQuery = ledgerQuery;
  }

  /**
   * {@code GET /api/ledger?dateFrom=&dateTo=&type=&accountId=&categoryId=&paymentMethodId=} - every
   * parameter optional. {@code type} is {@code INCOME|EXPENSE|TRANSFER}; a set {@code categoryId}
   * or {@code paymentMethodId} excludes transfers (they have neither); {@code accountId} matches a
   * transaction's account or either side of a transfer. At most 1,000 entries are returned, with
   * {@code truncated} set when more matched.
   */
  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "The newest-first entries, the scope's summary and the truncated flag")
  @ApiResponse(
      responseCode = "400",
      description =
          "type: urn:my-finances:error:method-argument-type-mismatch for an unparsable date, enum"
              + " or UUID",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public LedgerResponse list(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate dateTo,
      @RequestParam(required = false) LedgerEntryType type,
      @RequestParam(required = false) UUID accountId,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID paymentMethodId) {
    LedgerFilter filter =
        new LedgerFilter(dateFrom, dateTo, type, accountId, categoryId, paymentMethodId, null);
    return LedgerResponse.from(ledgerQuery.find(filter));
  }
}
