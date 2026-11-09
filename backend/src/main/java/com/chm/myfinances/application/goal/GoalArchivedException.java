package com.chm.myfinances.application.goal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation is not allowed on an archived goal (F039): turning "show on dashboard"
 * on, or replacing its allocations. Restore it first. Maps to 409.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class GoalArchivedException extends RuntimeException {

  public GoalArchivedException(UUID id) {
    super("Goal is archived: " + id);
  }
}
