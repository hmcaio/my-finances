package com.chm.myfinances.domain.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Budget} (PRD S5.6, F006 spec). Pure JUnit - no Spring context,
 * no database (ADR 0004) - written before {@link Budget} itself, per F006's plan.md.
 *
 * <p>{@code Budget} itself doesn't know or care whether {@code categoryId} points at an {@code
 * EXPENSE} category - that cross-aggregate check belongs to the application layer ({@code
 * BudgetService}, backed by F002's {@code CategoryRepository}), same convention as {@code Transfer}
 * not depending on {@code domain.account} for its own open-account check (F005).
 */
class BudgetTest {

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    Budget budget = Budget.create(id, categoryId);

    assertThat(budget.getId()).isEqualTo(id);
    assertThat(budget.getCategoryId()).isEqualTo(categoryId);
  }

  @Test
  void reconstituteRebuildsFromPersistedState() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();

    Budget budget = Budget.reconstitute(id, categoryId);

    assertThat(budget.getId()).isEqualTo(id);
    assertThat(budget.getCategoryId()).isEqualTo(categoryId);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> Budget.create(null, UUID.randomUUID()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullCategoryId() {
    assertThatThrownBy(() -> Budget.create(UUID.randomUUID(), null))
        .isInstanceOf(NullPointerException.class);
  }
}
