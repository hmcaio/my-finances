package com.chm.myfinances.application.backupstatus;

import java.time.Instant;

/**
 * The F018 backup sidecar's status marker (spec's Backend section), read from {@code status.json}
 * through {@link BackupStatusPort}. Fields mirror the marker's own documented shape (ADR 0015)
 * exactly - ids, timestamps and counts only, never an amount, description or entity name (ADR
 * 0011). {@code targetType} and {@code lastError} stay plain {@code String} (not an enum) because
 * they are opaque categories written by a separate shell-script codebase (the sidecar), not a value
 * this backend ever constructs or validates - {@link BackupStateDeriver} compares them
 * case-insensitively/by presence rather than parsing them strictly.
 */
public record BackupStatus(
    Instant lastSuccessAt,
    Instant lastAttemptAt,
    String lastError,
    String imageTag,
    String buildId,
    String schemaVersion,
    String targetType,
    boolean remoteConfigured,
    Boolean remoteOk,
    int count) {}
