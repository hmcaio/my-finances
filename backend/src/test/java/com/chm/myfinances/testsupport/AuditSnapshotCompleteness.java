package com.chm.myfinances.testsupport;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared helper for the per-aggregate {@code AuditSnapshotCompletenessTest} (F025 spec, ADR 0022):
 * "a completeness test with an explicit exclude list catches silent gaps" - this lists every field
 * a JPA entity declares, so each aggregate's test can assert its {@code toAuditSnapshot()} covers
 * all of them (minus that aggregate's own excludes: {@code id}, technical timestamps, a child
 * table's own foreign key back to its parent).
 */
public final class AuditSnapshotCompleteness {

  private AuditSnapshotCompleteness() {}

  /**
   * Every field this JPA entity class itself declares (not inherited from {@code AuditableEntity}).
   */
  public static Set<String> declaredFieldNames(Class<?> entityClass) {
    Set<String> names = new HashSet<>();
    for (Field field : entityClass.getDeclaredFields()) {
      names.add(field.getName());
    }
    return names;
  }

  /**
   * Fields every aggregate excludes: the primary key and the framework-managed audit timestamps.
   */
  public static Set<String> technicalFields() {
    return new HashSet<>(Arrays.asList("id", "createdAt", "lastModifiedAt"));
  }
}
