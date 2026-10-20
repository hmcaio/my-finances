package com.chm.myfinances.domain.vehicle;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Vehicle aggregate (PRD S5.11, F024 spec, ADR 0021). A flat, user-editable taxonomy entry labeling
 * which car a fuel-purchase {@code Transaction} belongs to — informational only, no
 * value/depreciation tracking (PRD S3 non-goal). No invariants beyond a non-blank, bounded name,
 * same shape as {@code PaymentMethod}.
 */
public final class Vehicle {

  private final UUID id;
  private String name;

  private Vehicle(UUID id, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
  }

  /** Creates a brand-new Vehicle. {@code id} must come from the {@code IdGenerator} port. */
  public static Vehicle create(UUID id, String name) {
    return new Vehicle(id, name);
  }

  /** Rebuilds a Vehicle from already-validated persisted state. */
  public static Vehicle reconstitute(UUID id, String name) {
    return new Vehicle(id, name);
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

  /** Flat snapshot of every persisted field (F025 spec, ADR 0022). */
  public Map<String, Object> toAuditSnapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", name);
    return snapshot;
  }
}
