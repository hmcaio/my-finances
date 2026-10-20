package com.chm.myfinances.application.auditlog;

import java.time.Instant;
import java.util.UUID;

/**
 * Optional filters for {@code GET /api/audit-log} (spec's API section): a date-range on {@code
 * occurredAt}, plus exact matches on entity type/id, action and origin. Every field may be {@code
 * null} (no filter on that dimension).
 */
public record AuditLogFilter(
    Instant from,
    Instant to,
    AuditEntityType entityType,
    AuditAction action,
    AuditOrigin origin,
    UUID entityId) {}
