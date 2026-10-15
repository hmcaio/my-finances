package com.chm.myfinances.domain.investmentcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment category aggregate (PRD S5.8, F008 spec): the top level of the two-level investment
 * taxonomy (Fixed Income, Variable Income, ...) - "category", not "type", because {@code type}
 * already means a fixed enum elsewhere in this codebase (ADR 0012).
 *
 * <p>F034/ADR 0028: every row is fixed, migration-seeded content - no user customization of any
 * kind. {@code create}/{@code rename} were deleted; {@link #reconstitute} is the only factory, used
 * solely to rebuild an instance from a persisted row.
 */
public final class InvestmentCategory {

  private final UUID id;
  private String name;

  private InvestmentCategory(UUID id, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireValidName(name);
  }

  /** Rebuilds a category from already-validated persisted state. */
  public static InvestmentCategory reconstitute(UUID id, String name) {
    return new InvestmentCategory(id, name);
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

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", name);
    return snapshot;
  }
}
