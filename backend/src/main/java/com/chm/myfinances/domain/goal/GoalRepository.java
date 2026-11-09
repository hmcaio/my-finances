package com.chm.myfinances.domain.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Goal} (ADR 0004). Implemented by an adapter in {@code
 * infrastructure/persistence/goal}.
 */
public interface GoalRepository {

  Goal save(Goal goal);

  Optional<Goal> findById(UUID id);

  List<Goal> findAll();

  /** Deletes the goal; its allocations go with it (database cascade). */
  void deleteById(UUID id);
}
