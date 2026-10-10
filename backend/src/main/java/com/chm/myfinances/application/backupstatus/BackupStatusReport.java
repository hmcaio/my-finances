package com.chm.myfinances.application.backupstatus;

import java.time.Instant;

/**
 * {@link BackupStatusService}'s output: the raw marker fields (or all-{@code null}/zero when there
 * is none) plus the two values {@link BackupStateDeriver} computes from them - F018 spec's Backend
 * section ("the marker fields plus a derived state ... and localOnly"). The controller's {@code
 * BackupStatusResponse} is a near-identical DTO, kept separate per the usual hexagonal boundary
 * (backend {@code CLAUDE.md}: application never depends on the web layer).
 */
public record BackupStatusReport(
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

  static BackupStatusReport unknown() {
    return new BackupStatusReport(
        BackupState.UNKNOWN, false, null, null, null, null, null, null, null, false, null, 0);
  }

  static BackupStatusReport of(BackupStatus status, BackupState state, boolean localOnly) {
    return new BackupStatusReport(
        state,
        localOnly,
        status.lastSuccessAt(),
        status.lastAttemptAt(),
        status.lastError(),
        status.imageTag(),
        status.buildId(),
        status.schemaVersion(),
        status.targetType(),
        status.remoteConfigured(),
        status.remoteOk(),
        status.count());
  }
}
