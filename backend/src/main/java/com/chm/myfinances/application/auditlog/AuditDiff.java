package com.chm.myfinances.application.auditlog;

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
 * <p>Only keys whose value actually differs (by {@link Objects#equals}) appear in the result - an
 * identical before/after produces an empty map, which is the signal {@link AuditRecorder} uses to
 * skip writing a no-op update (PRD S5.12: "an update that changes nothing is not logged").
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
      if (!Objects.equals(fromValue, toValue)) {
        changes.put(key, new FieldChange(fromValue, toValue));
      }
    }
    return changes;
  }
}
