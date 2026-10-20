package com.chm.myfinances.infrastructure.web.backupstatus;

import com.chm.myfinances.application.backupstatus.BackupState;
import com.chm.myfinances.application.backupstatus.BackupStatusReport;
import java.time.Instant;

/**
 * API representation of a {@link BackupStatusReport} (F018 spec's Backend section). Every field is
 * an id, a timestamp, a short category string or a count - never an amount, description or entity
 * name (ADR 0011; there is nothing of that shape in the marker to begin with).
 */
public record BackupStatusResponse(
    BackupState state,
    boolean localOnly,
    Instant lastSuccessAt,
    Instant lastAttemptAt,
    String lastError,
    String imageTag,
    String buildId,
    String schemaVersion,
    String targetType,
    boolean remoteConfigured,
    Boolean remoteOk,
    int count) {

  public static BackupStatusResponse from(BackupStatusReport report) {
    return new BackupStatusResponse(
        report.state(),
        report.localOnly(),
        report.lastSuccessAt(),
        report.lastAttemptAt(),
        report.lastError(),
        report.imageTag(),
        report.buildId(),
        report.schemaVersion(),
        report.targetType(),
        report.remoteConfigured(),
        report.remoteOk(),
        report.count());
  }
}
