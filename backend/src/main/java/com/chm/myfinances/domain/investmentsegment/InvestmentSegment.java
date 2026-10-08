package com.chm.myfinances.domain.investmentsegment;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment segment aggregate (F026 spec, ADR 0023): a flat, user-editable taxonomy entry
 * describing what kind of real estate an FII holds (Shoppings, Logistica, Papel, Lajes
 * Corporativas, ...), same shape as {@code InvestmentCategory}. Orthogonal to the
 * category/sub-category taxonomy - a sub-category like "REITs (FIIs)" is an asset class, a segment
 * is what kind of real estate it holds - so this is a new, independent aggregate, not a third
 * taxonomy tier. No import of any other aggregate's domain package.
 */
public final class InvestmentSegment {

  private final UUID id;
  private String name;

  private InvestmentSegment(UUID id, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireValidName(name);
  }

  /** Creates a brand-new segment. {@code id} must come from the {@code IdGenerator} port. */
  public static InvestmentSegment create(UUID id, String name) {
    return new InvestmentSegment(id, name);
  }

  /** Rebuilds a segment from already-validated persisted state. */
  public static InvestmentSegment reconstitute(UUID id, String name) {
    return new InvestmentSegment(id, name);
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
