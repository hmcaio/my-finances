package com.chm.myfinances.application.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuditRecorder}, written first (ADR 0004) against the hand-written {@link
 * FakeAuditLog} - plain JUnit, no Spring context. Spec's Domain/application section: the recorder
 * does the diff, skips an empty one, and fills in the entity type/id/label/action/origin a caller
 * hands it.
 */
class AuditRecorderTest {

  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final AuditRecorder recorder = new AuditRecorder(auditLog, AuditReferenceLabels.none());
  private final UUID entityId = UUID.randomUUID();

  @Test
  void recordCreateEmitsAnEntryWithNoFromSide() {
    recorder.recordCreate(
        AuditEntityType.CATEGORY, entityId, "Groceries", Map.of("name", "Groceries"));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.entityType()).isEqualTo(AuditEntityType.CATEGORY);
    assertThat(entry.entityId()).isEqualTo(entityId);
    assertThat(entry.entityLabel()).isEqualTo("Groceries");
    assertThat(entry.action()).isEqualTo(AuditAction.CREATE);
    assertThat(entry.origin()).isEqualTo(AuditOrigin.USER);
    assertThat(entry.changes()).containsEntry("name", new FieldChange(null, "Groceries"));
    assertThat(entry.requestId()).isNull();
  }

  @Test
  void recordUpdateEmitsAnEntryWithTheDiff() {
    recorder.recordUpdate(
        AuditEntityType.CATEGORY,
        entityId,
        "Supermarket",
        Map.of("name", "Groceries"),
        Map.of("name", "Supermarket"));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.UPDATE);
    assertThat(entry.changes()).containsEntry("name", new FieldChange("Groceries", "Supermarket"));
  }

  @Test
  void recordUpdateWithNoActualChangeSkipsTheWriteEntirely() {
    recorder.recordUpdate(
        AuditEntityType.CATEGORY,
        entityId,
        "Groceries",
        Map.of("name", "Groceries"),
        Map.of("name", "Groceries"));

    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void recordDeleteEmitsAnEntryWithNoToSide() {
    recorder.recordDelete(
        AuditEntityType.CATEGORY, entityId, "Groceries", Map.of("name", "Groceries"));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.DELETE);
    assertThat(entry.changes()).containsEntry("name", new FieldChange("Groceries", null));
  }

  @Test
  void recordActionAllowsAnArbitraryActionAndOrigin() {
    recorder.recordAction(
        AuditEntityType.ACCOUNT,
        entityId,
        "Nubank",
        AuditAction.CLOSE,
        Collections.singletonMap("closedDate", null),
        Map.of("closedDate", "2026-01-01"),
        AuditOrigin.USER);

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.CLOSE);
    assertThat(entry.origin()).isEqualTo(AuditOrigin.USER);
  }

  @Test
  void recordActionWithSystemOriginIsPreserved() {
    recorder.recordAction(
        AuditEntityType.RECURRING_TEMPLATE,
        entityId,
        "Rent",
        AuditAction.CLOSE,
        Map.of("active", true),
        Map.of("active", false),
        AuditOrigin.SYSTEM);

    assertThat(auditLog.onlyEntry().origin()).isEqualTo(AuditOrigin.SYSTEM);
  }

  @Test
  void recordActionWithNoChangeIsSkipped() {
    recorder.recordAction(
        AuditEntityType.RECURRING_TEMPLATE,
        entityId,
        "Rent",
        AuditAction.CLOSE,
        Map.of("active", false),
        Map.of("active", false),
        AuditOrigin.SYSTEM);

    assertThat(auditLog.entries()).isEmpty();
  }

  @Test
  void recordGeneratedEmitsASystemSummaryEntry() {
    recorder.recordGenerated(
        AuditEntityType.RECURRING_TEMPLATE,
        entityId,
        "Rent",
        Map.of("count", 3, "firstDate", "2026-01-01", "lastDate", "2026-03-01"));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.action()).isEqualTo(AuditAction.GENERATED);
    assertThat(entry.origin()).isEqualTo(AuditOrigin.SYSTEM);
    assertThat(entry.changes())
        .containsEntry("count", new FieldChange(null, 3))
        .containsEntry("firstDate", new FieldChange(null, "2026-01-01"))
        .containsEntry("lastDate", new FieldChange(null, "2026-03-01"));
  }

  @Test
  void recordCreateWithExplicitOriginIsPreserved() {
    recorder.recordCreate(
        AuditEntityType.RECURRING_TEMPLATE,
        entityId,
        "Rent",
        Map.of("description", "Rent"),
        AuditOrigin.SYSTEM);

    assertThat(auditLog.onlyEntry().origin()).isEqualTo(AuditOrigin.SYSTEM);
  }

  @Test
  void recordDeleteWithExplicitOriginIsPreserved() {
    recorder.recordDelete(
        AuditEntityType.INVESTMENT_HOLDING,
        entityId,
        "KNRI11 @ XP",
        Map.of("productId", "p"),
        AuditOrigin.SYSTEM);

    assertThat(auditLog.onlyEntry().origin()).isEqualTo(AuditOrigin.SYSTEM);
  }

  @Test
  void aKnownReferenceFieldIsWrappedWithItsResolvedLabelOnBothSides() {
    UUID fromCategoryId = UUID.randomUUID();
    UUID toCategoryId = UUID.randomUUID();
    AuditRecorder withResolver =
        new AuditRecorder(
            auditLog,
            new AuditReferenceLabels(
                Map.of(
                    "categoryId",
                    (Function<UUID, Optional<String>>)
                        id ->
                            id.equals(fromCategoryId)
                                ? Optional.of("Groceries")
                                : Optional.of("Transport"))));

    withResolver.recordUpdate(
        AuditEntityType.TRANSACTION,
        entityId,
        "Monthly shop",
        Map.of("categoryId", fromCategoryId.toString()),
        Map.of("categoryId", toCategoryId.toString()));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.changes())
        .containsEntry(
            "categoryId",
            new FieldChange(
                new ReferenceValue(fromCategoryId.toString(), "Groceries"),
                new ReferenceValue(toCategoryId.toString(), "Transport")));
  }

  @Test
  void aKnownReferenceFieldWithNoMatchingRowKeepsTheIdWithANullLabel() {
    UUID categoryId = UUID.randomUUID();
    AuditRecorder withResolver =
        new AuditRecorder(
            auditLog,
            new AuditReferenceLabels(
                Map.of("categoryId", (Function<UUID, Optional<String>>) id -> Optional.empty())));

    withResolver.recordCreate(
        AuditEntityType.TRANSACTION,
        entityId,
        "Monthly shop",
        Map.of("categoryId", categoryId.toString()));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.changes())
        .containsEntry(
            "categoryId", new FieldChange(null, new ReferenceValue(categoryId.toString(), null)));
  }

  @Test
  void anUnknownReferenceFieldIsLeftAsTheBareId() {
    UUID vehicleId = UUID.randomUUID();
    recorder.recordCreate(
        AuditEntityType.TRANSACTION, entityId, "Fuel", Map.of("vehicleId", vehicleId.toString()));

    AuditEntry entry = auditLog.onlyEntry();
    assertThat(entry.changes())
        .containsEntry("vehicleId", new FieldChange(null, vehicleId.toString()));
  }
}
