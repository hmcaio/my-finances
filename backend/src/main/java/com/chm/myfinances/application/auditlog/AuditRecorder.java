package com.chm.myfinances.application.auditlog;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Small helper every write use case calls instead of {@link AuditLog} directly (spec's Domain/
 * application section): computes the before/after diff via {@link AuditDiff}, skips the call
 * entirely when nothing actually changed, and fills in {@code origin} - so a use case only ever
 * supplies its own before/after snapshots and which action/origin applies.
 *
 * <p>{@code requestId} is deliberately never set here - it's {@code null} on every {@link
 * AuditEntry} this class builds; the {@link AuditLog} adapter fills it in from the MDC (ADR 0022).
 */
@Component
public class AuditRecorder {

  private final AuditLog auditLog;

  public AuditRecorder(AuditLog auditLog) {
    this.auditLog = auditLog;
  }

  /** {@code CREATE}, origin {@link AuditOrigin#USER}. */
  public void recordCreate(
      AuditEntityType entityType, UUID entityId, String entityLabel, Map<String, Object> after) {
    recordCreate(entityType, entityId, entityLabel, after, AuditOrigin.USER);
  }

  public void recordCreate(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      Map<String, Object> after,
      AuditOrigin origin) {
    record(entityType, entityId, entityLabel, AuditAction.CREATE, Map.of(), after, origin);
  }

  /** {@code UPDATE}, origin {@link AuditOrigin#USER}. */
  public void recordUpdate(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      Map<String, Object> before,
      Map<String, Object> after) {
    recordUpdate(entityType, entityId, entityLabel, before, after, AuditOrigin.USER);
  }

  public void recordUpdate(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      Map<String, Object> before,
      Map<String, Object> after,
      AuditOrigin origin) {
    record(entityType, entityId, entityLabel, AuditAction.UPDATE, before, after, origin);
  }

  /** {@code DELETE}, origin {@link AuditOrigin#USER}. */
  public void recordDelete(
      AuditEntityType entityType, UUID entityId, String entityLabel, Map<String, Object> before) {
    recordDelete(entityType, entityId, entityLabel, before, AuditOrigin.USER);
  }

  public void recordDelete(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      Map<String, Object> before,
      AuditOrigin origin) {
    record(entityType, entityId, entityLabel, AuditAction.DELETE, before, Map.of(), origin);
  }

  /**
   * The generic entry point for every other action ({@code CLOSE}/{@code REOPEN}/{@code STOPPED}),
   * which can be either {@link AuditOrigin#USER} (a direct click) or {@link AuditOrigin#SYSTEM} (a
   * cascade, e.g. closing an account deactivating its templates) depending on who triggered it -
   * the caller always says which.
   */
  public void recordAction(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      AuditAction action,
      Map<String, Object> before,
      Map<String, Object> after,
      AuditOrigin origin) {
    record(entityType, entityId, entityLabel, action, before, after, origin);
  }

  /**
   * {@code GENERATED}, always {@link AuditOrigin#SYSTEM} - the recurring catch-up's one summary
   * entry per template per run (PRD S5.12). {@code summary} is a create-shaped snapshot (no
   * before): nothing is skipped here because a caller never calls this with an empty summary - "a
   * run that generates nothing records nothing" is the catch-up service's own decision not to call
   * this method at all (spec's Domain/application section).
   */
  public void recordGenerated(
      AuditEntityType entityType, UUID entityId, String entityLabel, Map<String, Object> summary) {
    record(
        entityType,
        entityId,
        entityLabel,
        AuditAction.GENERATED,
        Map.of(),
        summary,
        AuditOrigin.SYSTEM);
  }

  private void record(
      AuditEntityType entityType,
      UUID entityId,
      String entityLabel,
      AuditAction action,
      Map<String, Object> before,
      Map<String, Object> after,
      AuditOrigin origin) {
    Map<String, FieldChange> changes = AuditDiff.diff(before, after);
    if (changes.isEmpty()) {
      return;
    }
    auditLog.record(
        new AuditEntry(entityType, entityId, entityLabel, action, origin, changes, null));
  }
}
