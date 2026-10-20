package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.DividendHistoryQuery;
import com.chm.myfinances.application.investmentreport.DividendTotalsGroupBy;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for dividend history (F026 spec, ADR 0023): {@code GET /api/fii/dividends?productId=&
 * from=&to=} and {@code GET /api/fii/dividends/totals?groupBy=TICKER|MONTH} - the totals response
 * shape depends on {@code groupBy} (a list of {@link DividendTotalByTickerResponse} or {@link
 * DividendTotalByMonthResponse}), same "one endpoint, two row shapes picked by a query param" as
 * {@code InvestmentAllocationController}'s own grouping param.
 */
@RestController
@RequestMapping("/api/fii/dividends")
public class DividendController {

  private final DividendHistoryQuery dividendHistoryQuery;

  public DividendController(DividendHistoryQuery dividendHistoryQuery) {
    this.dividendHistoryQuery = dividendHistoryQuery;
  }

  @GetMapping
  public List<DividendRowResponse> dividends(
      @RequestParam(required = false) UUID productId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return dividendHistoryQuery.dividends(productId, from, to).stream()
        .map(DividendRowResponse::from)
        .toList();
  }

  @GetMapping("/totals")
  public List<?> totals(
      @RequestParam DividendTotalsGroupBy groupBy,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return switch (groupBy) {
      case TICKER ->
          dividendHistoryQuery.totalsByTicker(from, to).stream()
              .map(DividendTotalByTickerResponse::from)
              .toList();
      case MONTH ->
          dividendHistoryQuery.totalsByMonth(from, to).stream()
              .map(DividendTotalByMonthResponse::from)
              .toList();
    };
  }
}
