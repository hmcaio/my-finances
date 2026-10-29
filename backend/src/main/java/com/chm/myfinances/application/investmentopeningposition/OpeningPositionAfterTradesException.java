package com.chm.myfinances.application.investmentopeningposition;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an opening position would be dated after its holding's earliest trade (F038 spec, ADR
 * 0033): the position is the start of the holding's tracked history, so an earlier trade would
 * double-count cotas. Maps to 409 - it depends on the holding's persisted trades. The same day is
 * allowed.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class OpeningPositionAfterTradesException extends RuntimeException {

  public OpeningPositionAfterTradesException(UUID holdingId) {
    super("Opening position would be dated after a trade of holding " + holdingId);
  }
}
