package com.chm.myfinances.domain.shared;

import java.util.UUID;

/**
 * Single point of entity-id generation (see ADR 0005).
 *
 * <p>The application layer's use cases call {@link #newId()} and pass the resulting id into an
 * aggregate's factory method. JPA entities never use {@code @GeneratedValue} — the id is already
 * assigned by the time an entity is mapped for persistence.
 */
public interface IdGenerator {

  UUID newId();
}
