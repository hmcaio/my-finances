package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.AllocationGrouping;
import com.chm.myfinances.application.investmentreport.InvestmentAllocationQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the investment allocation view (F009 spec; {@code groupBy=ACCOUNT} added by F023).
 */
@RestController
@RequestMapping("/api/investments/allocation")
public class InvestmentAllocationController {

  private final InvestmentAllocationQuery allocationQuery;
  private final Clock clock;

  public InvestmentAllocationController(InvestmentAllocationQuery allocationQuery, Clock clock) {
    this.allocationQuery = allocationQuery;
    this.clock = clock;
  }

  /**
   * {@code GET /api/investments/allocation?asOf=&groupBy=CATEGORY|SUBCATEGORY|ACCOUNT}: {@code
   * asOf} defaults to today, {@code groupBy} to {@code CATEGORY}.
   */
  @GetMapping
  public List<AllocationRowResponse> allocation(
      @RequestParam(name = "asOf", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate asOf,
      @RequestParam(name = "groupBy", defaultValue = "CATEGORY") AllocationGrouping groupBy) {
    return allocationQuery.allocation(asOf != null ? asOf : LocalDate.now(clock), groupBy).stream()
        .map(AllocationRowResponse::from)
        .toList();
  }
}
