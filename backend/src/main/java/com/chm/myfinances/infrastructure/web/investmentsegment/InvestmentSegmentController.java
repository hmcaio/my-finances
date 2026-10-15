package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.application.investmentsegment.InvestmentSegmentService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentSegment} (F026 spec).
 *
 * <p>F034/ADR 0028: read-only — {@code POST}/{@code PATCH}/{@code DELETE} were removed, every row
 * is fixed, migration-seeded content.
 */
@RestController
@RequestMapping("/api/investment-segments")
public class InvestmentSegmentController {

  private final InvestmentSegmentService segmentService;

  public InvestmentSegmentController(InvestmentSegmentService segmentService) {
    this.segmentService = segmentService;
  }

  @GetMapping
  public List<InvestmentSegmentResponse> list() {
    return segmentService.findAll().stream().map(InvestmentSegmentResponse::from).toList();
  }
}
