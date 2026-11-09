package com.chm.myfinances.application.goal;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a {@code Goal} id does not resolve to an existing goal (F039). Maps to 404. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class GoalNotFoundException extends RuntimeException {

  public GoalNotFoundException(UUID id) {
    super("Goal not found: " + id);
  }
}
