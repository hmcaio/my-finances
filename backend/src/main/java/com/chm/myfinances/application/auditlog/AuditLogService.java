package com.chm.myfinances.application.auditlog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * The one read use case for the Activity page (spec's API section): a filtered, paginated,
 * newest-first list of every audit entry. Thin wrapper over {@link AuditLogRepository}, same
 * "one @Service per aggregate" shape as every other use-case service in this codebase (backend
 * {@code CLAUDE.md}), even though there's only a single method here - this is a read-only
 * aggregate, with no write use cases of its own (writes go through {@link AuditRecorder}/{@link
 * AuditLog} from every other aggregate's own service).
 */
@Service
public class AuditLogService {

  private final AuditLogRepository auditLogRepository;

  public AuditLogService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  public Page<AuditLogRecord> findAll(AuditLogFilter filter, Pageable pageable) {
    return auditLogRepository.findAll(filter, pageable);
  }
}
