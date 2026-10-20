package com.chm.myfinances.application.auditlog;

/**
 * Who committed the change (PRD S5.12, ADR 0022). There is no {@code actor} - the app has no
 * authentication - so this is the only attribution an entry carries: a direct user action, or a
 * side effect (lazy recurring catch-up, a cascade such as closing an account deactivating its
 * templates) that no one directly clicked.
 */
public enum AuditOrigin {
  USER,
  SYSTEM
}
