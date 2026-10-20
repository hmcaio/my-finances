package com.chm.myfinances.application.auditlog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * One persisted audit row, read back for {@code GET /api/audit-log} (spec's API section). Unlike
 * {@link AuditEntry} (what a write use case hands the port), this carries the fields only the
 * adapter fills in at persist time: {@code id}, {@code occurredAt} and the resolved {@code
 * requestId}.
 */
public record AuditLogRecord(
    UUID id,
    Instant occurredAt,
    AuditEntityType entityType,
    UUID entityId,
    String entityLabel,
    AuditAction action,
    AuditOrigin origin,
    Map<String, FieldChange> changes,
    String requestId) {}
