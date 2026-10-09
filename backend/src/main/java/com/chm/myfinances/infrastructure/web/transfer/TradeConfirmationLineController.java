package com.chm.myfinances.infrastructure.web.transfer;

import com.chm.myfinances.application.transfer.TradeConfirmationLineQuery;
import java.util.List;
import java.util.UUID;
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
  public List<TradeConfirmationLineRecordResponse> findByProduct(@RequestParam UUID productId) {
    return query.findByProduct(productId).stream()
        .map(TradeConfirmationLineRecordResponse::from)
        .toList();
  }
}
