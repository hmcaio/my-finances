package com.chm.myfinances.infrastructure.web.auditlog;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditLogRecord;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.FieldChange;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * API representation of an {@link AuditLogRecord} (F025 spec's API section). {@code changes} is a
 * free-form object ({@code {field: {from, to}}}) - {@link FieldChange}'s {@code from}/{@code to}
 * are {@code Object}, so this stays a plain pass-through rather than a typed field list.
 */
public record AuditLogEntryResponse(
    UUID id,
    Instant occurredAt,
    AuditEntityType entityType,
    UUID entityId,
    String entityLabel,
    AuditAction action,
    AuditOrigin origin,
    Map<String, FieldChange> changes,
    String requestId) {

  public static AuditLogEntryResponse from(AuditLogRecord record) {
    return new AuditLogEntryResponse(
        record.id(),
        record.occurredAt(),
        record.entityType(),
        record.entityId(),
        record.entityLabel(),
        record.action(),
        record.origin(),
        record.changes(),
        record.requestId());
  }
}
