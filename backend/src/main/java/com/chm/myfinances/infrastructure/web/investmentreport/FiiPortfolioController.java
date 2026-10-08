package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.application.investmentreport.FiiPortfolioQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the FII portfolio summary (F026 spec, ADR 0023). {@code status} defaults to {@code
 * OPEN} - same F023 status-filter convention as {@code InvestmentProductController}.
 */
@RestController
@RequestMapping("/api/fii/portfolio")
public class FiiPortfolioController {

  private final FiiPortfolioQuery portfolioQuery;

  public FiiPortfolioController(FiiPortfolioQuery portfolioQuery) {
    this.portfolioQuery = portfolioQuery;
  }

  @GetMapping
  public List<FiiPortfolioRowResponse> portfolio(
      @RequestParam(required = false, defaultValue = "OPEN") InvestmentProductStatus status) {
    return portfolioQuery.portfolio(status).stream().map(FiiPortfolioRowResponse::from).toList();
  }
}
