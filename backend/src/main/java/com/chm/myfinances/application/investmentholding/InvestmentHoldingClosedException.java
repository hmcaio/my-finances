package com.chm.myfinances.application.investmentholding;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a buy/sell {@code Transfer} is created or edited against a closed {@code
 * InvestmentHolding} (F022 spec, replacing F009's {@code InvestmentProductClosedException}). Maps
 * to 409 - it depends on the holding's persisted state.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvestmentHoldingClosedException extends RuntimeException {

  public InvestmentHoldingClosedException(UUID holdingId) {
    super("Investment holding is closed and cannot take new trades: " + holdingId);
  }
}
