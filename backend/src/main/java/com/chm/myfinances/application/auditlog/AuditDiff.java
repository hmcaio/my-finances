package com.chm.myfinances.application.auditlog;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure function computing a field-by-field diff between two flat snapshots (spec's Domain/
 * application section, ADR 0022: "snapshot diff, not reflection"). {@code before}/{@code after} are
 * each aggregate's own {@code toAuditSnapshot()} output; either may be {@code null} or empty
 * ({@code CREATE} has no {@code before}, {@code DELETE} has no {@code after}).
 *
 * <p>Only keys whose value actually differs (by {@link Objects#equals}, amounts by value) appear in
 * the result - an identical before/after produces an empty map, which is the signal {@link
 * AuditRecorder} uses to skip writing a no-op update (PRD S5.12: "an update that changes nothing is
 * not logged").
 */
public final class AuditDiff {

  private AuditDiff() {}

  public static Map<String, FieldChange> diff(
      Map<String, Object> before, Map<String, Object> after) {
    Map<String, Object> from = before == null ? Map.of() : before;
    Map<String, Object> to = after == null ? Map.of() : after;

    Set<String> keys = new LinkedHashSet<>();
    keys.addAll(from.keySet());
    keys.addAll(to.keySet());

    Map<String, FieldChange> changes = new LinkedHashMap<>();
    for (String key : keys) {
      Object fromValue = from.get(key);
      Object toValue = to.get(key);
      if (!sameValue(fromValue, toValue)) {
        changes.put(key, new FieldChange(fromValue, toValue));
      }
    }
    return changes;
  }

  /**
   * {@link Objects#equals}, except that two {@link BigDecimal}s are equal when they are the same
   * amount: the database returns {@code 1000.00} for what a request carried as {@code 1000}, and
   * that is not a change worth an entry (F040).
   */
  private static boolean sameValue(Object a, Object b) {
    if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
      return x.compareTo(y) == 0;
    }
    return Objects.equals(a, b);
  }
}
