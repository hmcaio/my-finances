package com.chm.myfinances.application.auditlog;

/**
 * One field's before/after value in an {@link AuditEntry#changes()} diff (PRD S5.12). {@code from}
 * is absent (null) for a {@code CREATE}; {@code to} is absent (null) for a {@code DELETE}. Values
 * are whatever primitive/string {@code toAuditSnapshot()} put there - money as plain decimals,
 * dates as ISO strings, references as ids (spec's Domain/application section).
 */
public record FieldChange(Object from, Object to) {}
