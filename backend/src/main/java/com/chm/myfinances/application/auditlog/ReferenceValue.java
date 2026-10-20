package com.chm.myfinances.application.auditlog;

/**
 * A reference field's audited value: the raw id plus the referenced entity's label resolved at
 * write time (same transaction as the change, ADR 0022), so a deleted or later-renamed entity still
 * reads correctly on old audit rows. {@code label} is {@code null} when {@link
 * AuditReferenceLabels} has no resolver for the field (falls back to the bare id on the frontend)
 * or the referenced row no longer exists.
 */
public record ReferenceValue(String id, String label) {}
