package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when deleting an {@code InvestmentProduct} that still has at least one holding, even a
 * closed and empty one (F022 spec: a product can only be hard-deleted with zero holdings, not zero
 * history - a holding follows its own, stricter zero-history rule). Maps to 409, directing the user
 * to remove its holdings first.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductHasHoldingsException extends RuntimeException {

  public InvestmentProductHasHoldingsException(UUID id) {
    super("Investment product still has holdings and cannot be deleted: " + id);
  }
}
