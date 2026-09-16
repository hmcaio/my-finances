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
 */
public final class Category {

  private final UUID id;
  private String name;
  private final CategoryType type;

  private Category(UUID id, String name, CategoryType type) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
    this.type = Objects.requireNonNull(type, "type must not be null");
  }

  /** Creates a brand-new Category. {@code id} must come from the {@code IdGenerator} port. */
  public static Category create(UUID id, String name, CategoryType type) {
    return new Category(id, name, type);
  }

  /** Rebuilds a Category from already-validated persisted state. */
  public static Category reconstitute(UUID id, String name, CategoryType type) {
    return new Category(id, name, type);
  }

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
}
