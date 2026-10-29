package com.chm.myfinances.application.investmentsegment;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Read use case for {@link InvestmentSegment} (F026 spec, ADR 0023).
 *
 * <p>F034/ADR 0028: {@code create}/{@code rename}/{@code delete} were deleted — every row is now
 * fixed, migration-seeded content (the B3 FII sectors seeded by V24 and V26).
 */
@Service
public class InvestmentSegmentService {

  private final InvestmentSegmentRepository segmentRepository;

  public InvestmentSegmentService(InvestmentSegmentRepository segmentRepository) {
    this.segmentRepository = segmentRepository;
  }

  public List<InvestmentSegment> findAll() {
    return segmentRepository.findAll();
  }
}
