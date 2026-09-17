package com.chm.myfinances.domain.budget;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for {@link Budget} (ADR 0004: domain/application logic sits behind ports,
 * isolated from persistence details). Implemented by an adapter in {@code
 * infrastructure/persistence/budget}.
 *
 * <p>No {@code deleteById}/pagination - F006 spec's API surface has no delete-a-budget use case,
 * and {@code GET /api/budgets} is a plain list, not paginated (unlike F004/F005's transaction/
 * transfer lists - the number of budgeted categories is inherently small).
 */
public interface BudgetRepository {

  Budget save(Budget budget);

  Optional<Budget> findById(UUID id);

  List<Budget> findAll();

  /**
   * Whether a Budget already exists for {@code categoryId} - backs {@code BudgetService}'s
   * one-budget-per-category guard (PRD S5.6, F006 spec), mirroring the {@code
   * categories.category_id UNIQUE} constraint at the application layer.
   */
  boolean existsByCategoryId(UUID categoryId);
}
