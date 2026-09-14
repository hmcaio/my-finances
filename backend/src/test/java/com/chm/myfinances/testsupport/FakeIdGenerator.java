package com.chm.myfinances.testsupport;

import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Test double for {@link IdGenerator}, shared across application-service tests. Returns ids from a
 * fixed, ordered queue first — so a test can pin and assert on a specific value — then falls back
 * to a fresh random id once the queue is exhausted, since a fixed value for every call would make
 * repeated {@code create()} calls in the same test collide on the same key.
 */
public final class FakeIdGenerator implements IdGenerator {

  private final Deque<UUID> queuedIds;

  public FakeIdGenerator() {
    this(List.of());
  }

  public FakeIdGenerator(UUID... ids) {
    this(Arrays.asList(ids));
  }

  public FakeIdGenerator(List<UUID> ids) {
    this.queuedIds = new ArrayDeque<>(ids);
  }

  @Override
  public UUID newId() {
    UUID next = queuedIds.poll();
    return next != null ? next : UUID.randomUUID();
  }
}
