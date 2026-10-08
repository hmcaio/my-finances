package com.chm.myfinances.domain.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
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

  @Test
  void createAcceptsNameAtMaxLength() {
    String maxLengthName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH);

    Category category = Category.create(UUID.randomUUID(), maxLengthName, CategoryType.EXPENSE);

    assertThat(category.getName()).isEqualTo(maxLengthName);
  }

  @Test
  void createRejectsNameOverMaxLength() {
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> Category.create(UUID.randomUUID(), tooLongName, CategoryType.EXPENSE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void renameRejectsNameOverMaxLength() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);
    String tooLongName = "a".repeat(TextFieldConstraints.MAX_NAME_LENGTH + 1);

    assertThatThrownBy(() -> category.rename(tooLongName))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAlwaysYieldsANonBuiltInCategory() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    assertThat(category.isBuiltIn()).isFalse();
  }

  @Test
  void reconstitutePreservesTheBuiltInFlag() {
    UUID id = UUID.randomUUID();

    Category builtIn =
        Category.reconstitute(id, "Other Expense", CategoryType.EXPENSE, true, false);
    Category ordinary = Category.reconstitute(id, "Groceries", CategoryType.EXPENSE, false, false);

    assertThat(builtIn.isBuiltIn()).isTrue();
    assertThat(ordinary.isBuiltIn()).isFalse();
  }

  @Test
  void renameIsAllowedOnABuiltInCategoryAndKeepsTheFlagAndType() {
    Category category =
        Category.reconstitute(UUID.randomUUID(), "Other Income", CategoryType.INCOME, true, false);

    category.rename("Sem categoria");

    assertThat(category.getName()).isEqualTo("Sem categoria");
    assertThat(category.isBuiltIn()).isTrue();
    assertThat(category.getType()).isEqualTo(CategoryType.INCOME);
  }

  // F024 (ADR 0021): a second, independent flag - at most one row, immutable after creation like
  // builtIn. Whether rename/delete are blocked while it's set is a CategoryService concern (it's
  // a cross-cutting, state-dependent business rule); this class only carries the flag.

  @Test
  void createAlwaysYieldsANonFuelCategory() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    assertThat(category.isFuelCategory()).isFalse();
  }

  @Test
  void reconstitutePreservesTheFuelCategoryFlag() {
    UUID id = UUID.randomUUID();

    Category fuel = Category.reconstitute(id, "Fuel", CategoryType.EXPENSE, false, true);
    Category ordinary = Category.reconstitute(id, "Groceries", CategoryType.EXPENSE, false, false);

    assertThat(fuel.isFuelCategory()).isTrue();
    assertThat(ordinary.isFuelCategory()).isFalse();
  }

  @Test
  void builtInAndFuelCategoryFlagsAreIndependent() {
    Category category =
        Category.reconstitute(UUID.randomUUID(), "Fuel", CategoryType.EXPENSE, true, true);

    assertThat(category.isBuiltIn()).isTrue();
    assertThat(category.isFuelCategory()).isTrue();
  }

  // F026 (ADR 0023): a third, independent flag - at most one row, immutable after creation like
  // builtIn/fuelCategory. Whether rename/delete are blocked while it's set is a CategoryService
  // concern; this class only carries the flag.

  @Test
  void createAlwaysYieldsANonDividendCategory() {
    Category category = Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE);

    assertThat(category.isDividendCategory()).isFalse();
  }

  @Test
  void theShortReconstituteOverloadYieldsANonDividendCategory() {
    Category category =
        Category.reconstitute(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE, false, false);

    assertThat(category.isDividendCategory()).isFalse();
  }

  @Test
  void reconstitutePreservesTheDividendCategoryFlag() {
    UUID id = UUID.randomUUID();

    Category dividends =
        Category.reconstitute(id, "Dividends", CategoryType.INCOME, false, false, true);
    Category ordinary =
        Category.reconstitute(id, "Salary", CategoryType.INCOME, false, false, false);

    assertThat(dividends.isDividendCategory()).isTrue();
    assertThat(ordinary.isDividendCategory()).isFalse();
  }

  @Test
  void allThreeFlagsAreIndependent() {
    Category category =
        Category.reconstitute(
            UUID.randomUUID(), "Dividends", CategoryType.INCOME, true, true, true);

    assertThat(category.isBuiltIn()).isTrue();
    assertThat(category.isFuelCategory()).isTrue();
    assertThat(category.isDividendCategory()).isTrue();
  }
}
