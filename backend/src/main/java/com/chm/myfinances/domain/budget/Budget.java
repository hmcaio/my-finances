package com.chm.myfinances.domain.budget;

import java.util.Objects;
import java.util.UUID;

/**
 * Budget aggregate (PRD S5.6, F006 spec): one per expense category, owning the versioned monthly
 * cap history ({@link BudgetVersion}). PRD S5.6: "Budget (one per category)".
 *
 * <p>This class deliberately never checks {@code categoryId}'s {@code Category.type} - the domain
 * layer doesn't depend on {@code domain.category} (same standalone-aggregate style as {@code
 * Transfer} not depending on {@code domain.account} for its own account checks, F005). Rejecting a
 * non-{@code EXPENSE} category is an application-layer concern ({@code BudgetService}, backed by
 * F002's {@code CategoryRepository}), mapped to its own {@code @ResponseStatus} exception like
 * every other expected error case.
 *
 * <p>Like {@code BudgetVersion}, this aggregate has no delete use case (F006 spec's API surface is
 * create/list/edit-cap/report only) and no plain field mutator - {@code categoryId} is fixed for
 * the lifetime of a Budget.
 */
public final class Budget {

  private final UUID id;
  private final UUID categoryId;

  private Budget(UUID id, UUID categoryId) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
  }

  /** Creates a brand-new Budget. {@code id} must come from the {@code IdGenerator} port. */
  public static Budget create(UUID id, UUID categoryId) {
    return new Budget(id, categoryId);
  }

  /** Rebuilds a Budget from already-validated persisted state. */
  public static Budget reconstitute(UUID id, UUID categoryId) {
    return new Budget(id, categoryId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }
}
