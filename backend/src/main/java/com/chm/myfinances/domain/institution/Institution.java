package com.chm.myfinances.domain.institution;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.Objects;
import java.util.UUID;

/**
 * Institution aggregate (PRD S5.10, F017 spec). A flat, user-editable list of the banks, brokers
 * and issuers money sits at. Every {@code Account} references exactly one, by id.
 *
 * <p>One row is the built-in "No institution" fallback, identified by {@link #isBuiltIn()} rather
 * than by its name or a hard-coded id: it can be renamed but never deleted (the delete rule lives
 * in the application service). The flag is read-only from the application's point of view - {@link
 * #create} always yields a non-built-in institution, and only {@link #reconstitute} can carry the
 * flag in from the migration's seed row.
 */
public final class Institution {

  private final UUID id;
  private String name;
  private final boolean builtIn;

  private Institution(UUID id, String name, boolean builtIn) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireValidName(name);
    this.builtIn = builtIn;
  }

  /**
   * Creates a brand-new, non-built-in Institution. {@code id} must come from the {@code
   * IdGenerator} port.
   */
  public static Institution create(UUID id, String name) {
    return new Institution(id, name, false);
  }

  /** Rebuilds an Institution from already-validated persisted state. */
  public static Institution reconstitute(UUID id, String name, boolean builtIn) {
    return new Institution(id, name, builtIn);
  }

  /** Renames the institution, including the built-in one. */
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

  public boolean isBuiltIn() {
    return builtIn;
  }
}
