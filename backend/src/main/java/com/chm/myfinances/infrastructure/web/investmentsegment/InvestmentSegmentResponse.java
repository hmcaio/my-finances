package com.chm.myfinances.infrastructure.web.investmentsegment;

import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import java.util.UUID;

/** API representation of an {@link InvestmentSegment} (F026 spec). */
public record InvestmentSegmentResponse(UUID id, String name) {

  public static InvestmentSegmentResponse from(InvestmentSegment segment) {
    return new InvestmentSegmentResponse(segment.getId(), segment.getName());
  }
}
