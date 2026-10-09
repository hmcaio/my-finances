package com.chm.myfinances.domain.paymentmethod;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * PaymentMethod aggregate (PRD S5.2, F002 spec). A flat, user-editable taxonomy entry recording
 * which rail (debit card, PIX, cash, ...) a transaction went through — informational only, does not
 * affect balance math. No invariants beyond a non-blank name.
 */
public final class PaymentMethod {

  private final UUID id;
  private String name;

  private PaymentMethod(UUID id, String name) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.name = requireNonBlank(name);
  }

  /** Creates a brand-new PaymentMethod. {@code id} must come from the {@code IdGenerator} port. */
  public static PaymentMethod create(UUID id, String name) {
    return new PaymentMethod(id, name);
  }

  /** Rebuilds a PaymentMethod from already-validated persisted state. */
  public static PaymentMethod reconstitute(UUID id, String name) {
    return new PaymentMethod(id, name);
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
