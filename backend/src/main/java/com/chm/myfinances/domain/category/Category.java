package com.chm.myfinances.domain.category;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
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
 *
 * <p>{@link #isFuelCategory()} (F024, ADR 0021) is a second, independent flag — at most one row
 * carries it (DB partial unique index, {@code V18}) — deliberately not unified with {@code
 * builtIn}, which means something else ("system default, protected from deletion, freely
 * renamable"). Unlike {@code builtIn}, the fuel category is structurally load-bearing for the
 * {@code Transaction.fuelDetails} invariant, so both delete <b>and</b> rename are blocked while it
 * is set — enforced in {@code CategoryService}, same layer as the {@code builtIn} delete guard, not
 * here: this class only carries the flag. Same read-only shape as {@code builtIn} - {@link #create}
 * always yields {@code false}, only {@link #reconstitute} can carry it in.
 *
 * <p>{@link #isDividendCategory()} (F026, ADR 0023) is a third, independent flag — at most one row
 * carries it (DB partial unique index, {@code V20}) — same read-only shape and same "delete and
 * rename both blocked while set" load-bearing reasoning as {@code fuelCategory} (the {@code
 * Transaction.investmentHoldingId} invariant, enforced in {@code CategoryService}/{@code
 * TransactionService}, not here).
 */
public final class Category {

  private final UUID id;
  private String name;
  private final CategoryType type;
  private final boolean builtIn;
  private final boolean fuelCategory;
  private final boolean dividendCategory;

  private Category(
      UUID id,
      String name,
      CategoryType type,
      boolean builtIn,
      boolean fuelCategory,
      boolean dividendCategory) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.builtIn = builtIn;
    this.fuelCategory = fuelCategory;
    this.dividendCategory = dividendCategory;
  }

  /**
   * Creates a brand-new, non-built-in, non-fuel, non-dividend Category. {@code id} must come from
   * the {@code IdGenerator} port.
   */
  public static Category create(UUID id, String name, CategoryType type) {
    return new Category(id, name, type, false, false, false);
  }

  /** Rebuilds a Category (never the dividend category) from already-validated persisted state. */
  public static Category reconstitute(
      UUID id, String name, CategoryType type, boolean builtIn, boolean fuelCategory) {
    return reconstitute(id, name, type, builtIn, fuelCategory, false);
  }

  /** Rebuilds a Category, including its dividend-category flag (F026), from persisted state. */
  public static Category reconstitute(
      UUID id,
      String name,
      CategoryType type,
      boolean builtIn,
      boolean fuelCategory,
      boolean dividendCategory) {
    return new Category(id, name, type, builtIn, fuelCategory, dividendCategory);
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

  /**
   * Whether this is the single dedicated fuel category (F024, ADR 0021): a {@code Transaction}
   * carries {@code FuelDetails} if and only if its category is this one.
   */
  public boolean isFuelCategory() {
    return fuelCategory;
  }

  /**
   * Whether this is the single dedicated dividend category (F026, ADR 0023): a {@code Transaction}
   * carries an {@code investmentHoldingId} if and only if its category is this one.
   */
  public boolean isDividendCategory() {
    return dividendCategory;
  }

  /**
   * Flat snapshot of every persisted field (F025 spec, ADR 0022), used to compute a before/after
   * diff for the audit log.
   */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", name);
    snapshot.put("type", type.name());
    snapshot.put("builtIn", builtIn);
    snapshot.put("fuelCategory", fuelCategory);
    snapshot.put("dividendCategory", dividendCategory);
    return snapshot;
  }
}
