package com.chm.myfinances.domain.investmentcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment category aggregate (PRD S5.8, F008 spec): the top level of the two-level investment
 * taxonomy (Fixed Income, Variable Income, ...). A flat, user-editable entry, same shape as F002's
 * {@code Category} but a separate entity/table - "category", not "type", because {@code type}
 * already means a fixed enum elsewhere in this codebase (ADR 0012).
 */
public final class InvestmentCategory {

  private final UUID id;
  private String name;

  private InvestmentCategory(UUID id, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireValidName(name);
  }

  /** Creates a brand-new category. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentCategory create(UUID id, String name) {
    return new InvestmentCategory(id, name);
  }

  /** Rebuilds a category from already-validated persisted state. */
  public static InvestmentCategory reconstitute(UUID id, String name) {
    return new InvestmentCategory(id, name);
  }

  public void rename(String newName) {
    this.name = requireValidName(newName);
  }

  private static String requireValidName(String value) {
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
}
