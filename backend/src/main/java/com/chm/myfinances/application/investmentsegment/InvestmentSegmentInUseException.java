package com.chm.myfinances.application.investmentsegment;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentSegment} still referenced by at least one {@code
 * InvestmentProduct} (F026 spec) - same referenced-by-product delete guard shape as {@code
 * InvestmentSubcategoryInUseException}.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentSegmentInUseException extends RuntimeException {

  public InvestmentSegmentInUseException(UUID id) {
    super("Investment segment is still referenced by a product: " + id);
  }
}
