package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.application.investmentreport.FiiPortfolioQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the FII portfolio summary (F026 spec, ADR 0023). {@code status} defaults to {@code
 * OPEN} - same F023 status-filter convention as {@code InvestmentProductController}. {@code month}
 * (Addendum - Month Selector) defaults to the current month and is converted to an as-of date
 * server-side with the F009/F010 convention (month-end, except the current month evaluated at
 * today) before being threaded into {@link FiiPortfolioQuery}.
 */
@RestController
@RequestMapping("/api/fii/portfolio")
public class FiiPortfolioController {

  private final FiiPortfolioQuery portfolioQuery;
  private final Clock clock;

  public FiiPortfolioController(FiiPortfolioQuery portfolioQuery, Clock clock) {
    this.portfolioQuery = portfolioQuery;
    this.clock = clock;
  }

  @GetMapping
  public List<FiiPortfolioRowResponse> portfolio(
      @RequestParam(required = false, defaultValue = "OPEN") InvestmentProductStatus status,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    YearMonth effectiveMonth = month != null ? month : YearMonth.now(clock);
    LocalDate today = LocalDate.now(clock);
    LocalDate monthEnd = effectiveMonth.atEndOfMonth();
    LocalDate asOf = monthEnd.isAfter(today) ? today : monthEnd;
    return portfolioQuery.portfolio(status, asOf).stream()
        .map(FiiPortfolioRowResponse::from)
        .toList();
  }
}
