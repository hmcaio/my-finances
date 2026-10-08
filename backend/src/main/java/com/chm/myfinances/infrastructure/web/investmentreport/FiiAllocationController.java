package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.FiiAllocationBasis;
import com.chm.myfinances.application.investmentreport.FiiAllocationGroupBy;
import com.chm.myfinances.application.investmentreport.FiiAllocationQuery;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for the four FII allocation chart slices (F026 spec, ADR 0023): {@code
 * ?basis=ACTUAL|PLANNED&groupBy=TICKER|SEGMENT}.
 */
@RestController
@RequestMapping("/api/fii/allocation")
public class FiiAllocationController {

  private final FiiAllocationQuery allocationQuery;

  public FiiAllocationController(FiiAllocationQuery allocationQuery) {
    this.allocationQuery = allocationQuery;
  }

  @GetMapping
  public List<FiiAllocationRowResponse> allocation(
      @RequestParam FiiAllocationBasis basis, @RequestParam FiiAllocationGroupBy groupBy) {
    return allocationQuery.allocation(basis, groupBy).stream()
        .map(FiiAllocationRowResponse::from)
        .toList();
  }
}
