package com.chm.myfinances.application.auditlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Read-side port for the Activity page (spec's API section): {@code GET /api/audit-log}'s filtered,
 * paginated, newest-first list. Separate from the write-only {@link AuditLog} port - a write use
 * case never needs to read the log back, and keeping the two apart means {@link AuditRecorder} (and
 * every write use case it serves) depends on nothing but the narrow {@code record(AuditEntry)}
 * method.
 */
public interface AuditLogRepository {

  Page<AuditLogRecord> findAll(AuditLogFilter filter, Pageable pageable);
}
