package com.chm.myfinances.application.investmentsegment;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when an {@code InvestmentSegment} id doesn't resolve to a persisted segment (F026). */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvestmentSegmentNotFoundException extends RuntimeException {

  public InvestmentSegmentNotFoundException(UUID id) {
    super("Investment segment not found: " + id);
  }
}
