package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.InvestmentValueSeriesQuery;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API for the monthly per-product value series (F009 spec). */
@RestController
@RequestMapping("/api/investments/value-series")
public class InvestmentValueSeriesController {

  /** Default window when {@code from} is omitted: the last twelve months up to {@code to}. */
  private static final int DEFAULT_MONTHS = 12;

  private final InvestmentValueSeriesQuery valueSeriesQuery;
  private final Clock clock;

  public InvestmentValueSeriesController(InvestmentValueSeriesQuery valueSeriesQuery, Clock clock) {
    this.valueSeriesQuery = valueSeriesQuery;
    this.clock = clock;
  }

  /**
   * {@code GET /api/investments/value-series?from=&to=&productId=}: {@code from}/{@code to} are
   * {@code yyyy-MM}; {@code to} defaults to the current month and {@code from} to eleven months
   * before {@code to}. Omitting {@code productId} returns one series per product.
   */
  @GetMapping
  public List<ProductSeriesResponse> valueSeries(
      @RequestParam(name = "from", required = false) @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth from,
      @RequestParam(name = "to", required = false) @DateTimeFormat(pattern = "yyyy-MM")
          YearMonth to,
      @RequestParam(name = "productId", required = false) UUID productId) {
    YearMonth effectiveTo = to != null ? to : YearMonth.now(clock);
    YearMonth effectiveFrom = from != null ? from : effectiveTo.minusMonths(DEFAULT_MONTHS - 1);
    return valueSeriesQuery.series(effectiveFrom, effectiveTo, productId).stream()
        .map(ProductSeriesResponse::from)
        .toList();
  }
}
