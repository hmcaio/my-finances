package com.chm.myfinances.application.auditlog;

import java.util.Map;
import java.util.UUID;

/**
 * One append-only audit row to record (spec's Domain/application section, PRD S5.12). {@code
 * requestId} is always {@code null} from a caller's point of view - the {@link AuditLog} adapter
 * fills it in from the MDC (ADR 0022's "same request id" grouping), never a caller.
 *
 * <p>{@code occurredAt} isn't a field here: the adapter stamps it from its own injected {@code
 * Clock} at persist time (spec's Persistence section), so every caller is spared from threading a
 * clock through just to build this record.
 */
public record AuditEntry(
    AuditEntityType entityType,
    UUID entityId,
    String entityLabel,
    AuditAction action,
    AuditOrigin origin,
    Map<String, FieldChange> changes,
    String requestId) {}
