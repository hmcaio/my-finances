package com.chm.myfinances.domain.category;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.Objects;
import java.util.UUID;

/**
 * Category aggregate (PRD S5.1, F002 spec). A flat, user-editable taxonomy entry used to classify
 * transactions as income or expense.
 *
 * <p>Renaming is allowed at any time; {@code type} is fixed at creation and deliberately has no
 * mutator anywhere on this class — changing a category's income/expense type after it may already
 * have transactions attached would silently corrupt budget and net-worth math, so the domain model
 * simply never exposes a way to do it (see F002 spec's "type is immutable after creation").
 *
 * <p>One row per type is the built-in fallback ("Other Expense", "Other Income"), identified by
 * {@link #isBuiltIn()} rather than by its name or a hard-coded id: it can be renamed but never
 * deleted (the delete rule lives in the application service). The flag is read-only from the
 * application's point of view - {@link #create} always yields a non-built-in category, and only
 * {@link #reconstitute} can carry the flag in from the migration's rows.
 */
public final class Category {

  private final UUID id;
  private String name;
  private final CategoryType type;
  private final boolean builtIn;

  private Category(UUID id, String name, CategoryType type, boolean builtIn) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.builtIn = builtIn;
  }

  /**
   * Creates a brand-new, non-built-in Category. {@code id} must come from the {@code IdGenerator}
   * port.
   */
  public static Category create(UUID id, String name, CategoryType type) {
    return new Category(id, name, type, false);
  }

  /** Rebuilds a Category from already-validated persisted state. */
  public static Category reconstitute(UUID id, String name, CategoryType type, boolean builtIn) {
    return new Category(id, name, type, builtIn);
  }

  /** Renames the category, including a built-in one. */
  public void rename(String newName) {
    this.name = requireNonBlank(newName);
  }

  private static String requireNonBlank(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "name must not exceed " + TextFieldConstraints.MAX_NAME_LENGTH + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public CategoryType getType() {
    return type;
  }

  public boolean isBuiltIn() {
    return builtIn;
  }
}
