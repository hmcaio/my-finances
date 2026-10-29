package com.chm.myfinances.application.transfer;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a trade confirmation line is dated before its holding's opening position (F038 spec,
 * ADR 0033): the position is the start of the holding's tracked history, so an earlier trade would
 * double-count cotas. Maps to 409 - it depends on the holding's persisted position. The same day is
 * allowed.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class TradeBeforeOpeningPositionException extends RuntimeException {

  public TradeBeforeOpeningPositionException(UUID holdingId) {
    super("Trade is dated before the opening position of holding " + holdingId);
  }
}
