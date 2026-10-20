package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.application.transfer.TradeConfirmationLineQuery;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the F027 line-granularity trade read ({@code GET /api/trade-confirmation-lines}).
 */
@RestController
@RequestMapping("/api/trade-confirmation-lines")
public class TradeConfirmationLineController {

  private final TradeConfirmationLineQuery query;

  public TradeConfirmationLineController(TradeConfirmationLineQuery query) {
    this.query = query;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "404",
      description = "type: urn:my-finances:error:investment-product-not-found",
      content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public List<TradeConfirmationLineRecordResponse> findByProduct(@RequestParam UUID productId) {
    return query.findByProduct(productId).stream()
        .map(TradeConfirmationLineRecordResponse::from)
        .toList();
  }
}
