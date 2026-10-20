package com.chm.myfinances.application.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditDiff}, written first (ADR 0004): a pure function, no Spring context. ADR
 * 0022/spec's Domain/application section: {@code CREATE}'s {@code from} side is absent, {@code
 * DELETE}'s {@code to} side is absent, and an unchanged field produces no entry at all (an empty
 * diff tells the caller to skip the write).
 */
class AuditDiffTest {

  @Test
  void createDiffHasNoFromSideForAnyField() {
    Map<String, Object> after = Map.of("name", "Groceries", "type", "EXPENSE");

    Map<String, FieldChange> changes = AuditDiff.diff(Map.of(), after);

    assertThat(changes)
        .containsEntry("name", new FieldChange(null, "Groceries"))
        .containsEntry("type", new FieldChange(null, "EXPENSE"));
  }

  @Test
  void deleteDiffHasNoToSideForAnyField() {
    Map<String, Object> before = Map.of("name", "Groceries", "type", "EXPENSE");

    Map<String, FieldChange> changes = AuditDiff.diff(before, Map.of());

    assertThat(changes)
        .containsEntry("name", new FieldChange("Groceries", null))
        .containsEntry("type", new FieldChange("EXPENSE", null));
  }

  @Test
  void updateDiffOnlyIncludesFieldsThatActuallyChanged() {
    Map<String, Object> before = Map.of("name", "Groceries", "type", "EXPENSE");
    Map<String, Object> after = Map.of("name", "Supermarket", "type", "EXPENSE");

    Map<String, FieldChange> changes = AuditDiff.diff(before, after);

    assertThat(changes).containsOnlyKeys("name");
    assertThat(changes.get("name")).isEqualTo(new FieldChange("Groceries", "Supermarket"));
  }

  @Test
  void identicalSnapshotsProduceAnEmptyDiff() {
    Map<String, Object> snapshot = Map.of("name", "Groceries", "type", "EXPENSE");

    Map<String, FieldChange> changes =
        AuditDiff.diff(snapshot, Map.of("name", "Groceries", "type", "EXPENSE"));

    assertThat(changes).isEmpty();
  }

  @Test
  void aFieldThatBecomesNullIsStillRecordedAsAChange() {
    Map<String, Object> before = Map.of("additionalNotes", "secret");
    Map<String, Object> after = Map.of();

    Map<String, FieldChange> changes = AuditDiff.diff(before, after);

    assertThat(changes).containsEntry("additionalNotes", new FieldChange("secret", null));
  }

  @Test
  void aFieldThatAppearsFromNullIsStillRecordedAsAChange() {
    Map<String, Object> before = Map.of();
    Map<String, Object> after = Map.of("additionalNotes", "now set");

    Map<String, FieldChange> changes = AuditDiff.diff(before, after);

    assertThat(changes).containsEntry("additionalNotes", new FieldChange(null, "now set"));
  }

  @Test
  void bothSnapshotsEmptyProducesAnEmptyDiff() {
    assertThat(AuditDiff.diff(Map.of(), Map.of())).isEmpty();
  }

  @Test
  void nullSnapshotsAreTreatedAsEmpty() {
    Map<String, FieldChange> changes = AuditDiff.diff(null, Map.of("name", "Groceries"));

    assertThat(changes).containsEntry("name", new FieldChange(null, "Groceries"));
  }
}
