package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for {@code InvestmentSegment} (F026 spec).
 *
 * <p>F034/ADR 0028: read-only — {@code POST}/{@code PATCH}/{@code DELETE} were removed, every row
 * is fixed, migration-seeded content. With no rule left to apply there is no use-case service: the
 * controller reads the port directly (docs/conventions/backend-structure.md).
 */
@RestController
@RequestMapping("/api/investment-segments")
public class InvestmentSegmentController {

  private final InvestmentSegmentRepository segmentRepository;

  public InvestmentSegmentController(InvestmentSegmentRepository segmentRepository) {
    this.segmentRepository = segmentRepository;
  }

  @GetMapping
  public List<InvestmentSegmentResponse> list() {
    return segmentRepository.findAll().stream().map(InvestmentSegmentResponse::from).toList();
  }
}
