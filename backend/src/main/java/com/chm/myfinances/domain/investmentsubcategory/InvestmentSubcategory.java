package com.chm.myfinances.domain.investmentsubcategory;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Investment sub-category aggregate (PRD S5.8, F008 spec): the second level of the investment
 * taxonomy, always under exactly one category (CDB and LCI under Fixed Income, ...).
 *
 * <p>{@code investmentCategoryId} is fixed at reconstitution and has no mutator anywhere on this
 * class - re-parenting would silently reclassify every product beneath the sub-category. The parent
 * is held by id only: this class doesn't import {@code domain/investmentcategory}.
 *
 * <p>F034/ADR 0028: every row is fixed, migration-seeded content - no user customization of any
 * kind. {@code create}/{@code rename} were deleted; {@link #reconstitute} is the only factory, used
 * solely to rebuild an instance from a persisted row.
 */
public final class InvestmentSubcategory {

  private final UUID id;
  private final UUID investmentCategoryId;
  private String name;

  private InvestmentSubcategory(UUID id, UUID investmentCategoryId, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.investmentCategoryId =
        Objects.requireNonNull(investmentCategoryId, "investmentCategoryId must not be null");
    this.name = requireValidName(name);
  }

  /** Rebuilds a sub-category from already-validated persisted state. */
  public static InvestmentSubcategory reconstitute(
      UUID id, UUID investmentCategoryId, String name) {
    return new InvestmentSubcategory(id, investmentCategoryId, name);
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

  public UUID getInvestmentCategoryId() {
    return investmentCategoryId;
  }

  public String getName() {
    return name;
  }

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("investmentCategoryId", investmentCategoryId.toString());
    snapshot.put("name", name);
    return snapshot;
  }
}
