package com.chm.myfinances.application.auditlog;

/** Every action an {@link AuditEntry} can record (PRD S5.12, ADR 0022). */
public enum AuditAction {
  CREATE,
  UPDATE,
  DELETE,
  CLOSE,
  REOPEN,
  /** A budget tombstone (issue #61) - "no budget/allocation from this month on". */
  STOPPED,
  /** A recurring catch-up run's one summary entry per template (F007, ADR 0003). */
  GENERATED
}
