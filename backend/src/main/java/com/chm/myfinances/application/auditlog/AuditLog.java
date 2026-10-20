package com.chm.myfinances.application.auditlog;

/**
 * Output port for recording one audit entry (ADR 0022). Called explicitly from an application write
 * use case, inside that use case's own transaction - never a domain event, never a trigger: if the
 * insert fails, the business write fails with it (ADR 0022's "same transaction" decision).
 *
 * <p>Callers never use this port directly - they go through {@link AuditRecorder}, which does the
 * before/after diff and skips a no-op update.
 */
public interface AuditLog {

  void record(AuditEntry entry);
}
