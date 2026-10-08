package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.FiiAllocationBasis;
import com.chm.myfinances.application.investmentreport.FiiAllocationGroupBy;
import com.chm.myfinances.application.investmentreport.FiiAllocationQuery;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the four FII allocation chart slices (F026 spec, ADR 0023): {@code
 * ?basis=ACTUAL|PLANNED&groupBy=TICKER|SEGMENT}. {@code month} (Addendum - Month Selector) defaults
 * to the current month; conversion to an as-of date for the {@code ACTUAL} basis happens inside
 * {@link FiiAllocationQuery}, not here.
 */
@RestController
@RequestMapping("/api/fii/allocation")
public class FiiAllocationController {

  private final FiiAllocationQuery allocationQuery;
  private final Clock clock;

  public FiiAllocationController(FiiAllocationQuery allocationQuery, Clock clock) {
    this.allocationQuery = allocationQuery;
    this.clock = clock;
  }

  @GetMapping
  public List<FiiAllocationRowResponse> allocation(
      @RequestParam FiiAllocationBasis basis,
      @RequestParam FiiAllocationGroupBy groupBy,
      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
    YearMonth effectiveMonth = month != null ? month : YearMonth.now(clock);
    return allocationQuery.allocation(basis, groupBy, effectiveMonth).stream()
        .map(FiiAllocationRowResponse::from)
        .toList();
  }
}
