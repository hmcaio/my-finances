package com.chm.myfinances.application.allocationplan;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an allocation plan's entries don't sum to exactly 100% (F026 spec, ADR 0023). Maps to
 * 400 - a self-contained request-shape error, knowable from the request body alone.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AllocationPlanSumInvalidException extends RuntimeException {

  public AllocationPlanSumInvalidException(BigDecimal actualSum) {
    super("Allocation plan entries must sum to exactly 100, got " + actualSum);
  }
}
