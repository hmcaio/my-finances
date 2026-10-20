package com.chm.myfinances.infrastructure.web.auditlog;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditLogFilter;
import com.chm.myfinances.application.auditlog.AuditLogRecord;
import com.chm.myfinances.application.auditlog.AuditLogService;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST API for the Activity page (F025 spec's API section): {@code GET /api/audit-log?
 * from=&to=&entityType=&action=&origin=&entityId=&page=&size=}, newest first ({@code occurredAt}
 * descending, {@code id} descending as the tiebreaker). No write endpoints - nothing deletes or
 * edits a row (PRD S5.12/S6.12).
 */
@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

  private final AuditLogService auditLogService;

  public AuditLogController(AuditLogService auditLogService) {
    this.auditLogService = auditLogService;
  }

  @GetMapping
  public PagedModel<AuditLogEntryResponse> list(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) AuditEntityType entityType,
      @RequestParam(required = false) AuditAction action,
      @RequestParam(required = false) AuditOrigin origin,
      @RequestParam(required = false) UUID entityId,
      @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    AuditLogFilter filter = new AuditLogFilter(from, to, entityType, action, origin, entityId);
    Pageable ordered = withIdTiebreaker(pageable);
    Page<AuditLogRecord> page = auditLogService.findAll(filter, ordered);
    return new PagedModel<>(page.map(AuditLogEntryResponse::from));
  }

  /**
   * Appends {@code id desc} as a tiebreaker after whatever sort the caller asked for (default
   * {@code occurredAt desc}) - spec's API section: "ordered occurred_at desc, id desc" - so two
   * entries written in the same millisecond still come back in a stable order across pages.
   */
  private static Pageable withIdTiebreaker(Pageable pageable) {
    Sort sort = pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"));
    return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
  }
}
