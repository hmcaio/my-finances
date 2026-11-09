package com.chm.myfinances.application.goal;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown for a goal or allocation that is wrong regardless of what is persisted (F039): a value the
 * domain rejects (the service translates its {@link IllegalArgumentException}, as F038's {@code
 * InvalidOpeningPositionException} does), a target date that is not after today, or a duplicate
 * product in an allocation set. Maps to 400.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidGoalException extends RuntimeException {

  public InvalidGoalException(String reason) {
    super("Invalid goal: " + reason);
  }
}
