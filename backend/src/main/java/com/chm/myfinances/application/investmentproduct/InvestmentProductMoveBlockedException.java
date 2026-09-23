package com.chm.myfinances.application.investmentproduct;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when editing an {@code InvestmentProduct} to a different account once it has snapshots or
 * buy/sell transfers: its past trades point at the old account, so their derived direction and the
 * value series' contributions would misread. Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentProductMoveBlockedException extends RuntimeException {

  public InvestmentProductMoveBlockedException(UUID id) {
    super("Investment product has history and cannot move to another account: " + id);
  }
}
