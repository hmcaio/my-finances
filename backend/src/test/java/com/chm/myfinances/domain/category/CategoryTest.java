package com.chm.myfinances.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link Category} (PRD S5.1, F002 spec). Pure JUnit — no Spring
 * context, no database (ADR 0004) — written before {@link Category} itself, per F002's plan.md.
 */
class CategoryTest {

  @Test
  void createsWithGivenIdNameAndType() {
    UUID id = UUID.randomUUID();

    Category category = Category.create(id, "Groceries", CategoryType.EXPENSE);

    assertThat(category.getId()).isEqualTo(id);
    assertThat(category.getName()).isEqualTo("Groceries");
    assertThat(category.getType()).isEqualTo(CategoryType.EXPENSE);
  }

  @Test
  void renameChangesNameOnly() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    category.rename("Groceries & Household");

    assertThat(category.getName()).isEqualTo("Groceries & Household");
  }

  @Test
  void typeIsImmutableAfterCreation() {
    // Renaming — the only mutation Category exposes — must never change type. Category has no
    // setter/mutator for type at all: this is enforced structurally (a final field with only a
    // getter), not by a runtime check, because changing a category's income/expense type after
    // creation would silently corrupt budget/net-worth math for any existing transaction already
    // referencing it (F002 spec).
    Category category = Category.create(UUID.randomUUID(), "Salary", CategoryType.INCOME);

    category.rename("Salary (renamed)");

    assertThat(category.getType()).isEqualTo(CategoryType.INCOME);
  }

  @Test
  void createRejectsBlankName() {
    assertThatThrownBy(() -> Category.create(UUID.randomUUID(), "  ", CategoryType.EXPENSE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsNullType() {
    assertThatThrownBy(() -> Category.create(UUID.randomUUID(), "Groceries", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void renameRejectsBlankName() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    assertThatThrownBy(() -> category.rename(" ")).isInstanceOf(IllegalArgumentException.class);
  }
}
