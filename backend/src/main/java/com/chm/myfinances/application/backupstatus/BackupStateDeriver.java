package com.chm.myfinances.application.backupstatus;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure derivation of {@link BackupState} and the separate {@code localOnly} flag from a {@link
 * BackupStatus} (or its absence) - F018 spec's Backend section, tested first (plan.md Phase 4). No
 * I/O, no Spring: {@link BackupStatusService} is the only caller.
 *
 * <p>{@code FAILING} takes priority over {@code STALE} when both conditions hold (a failed last
 * attempt whose last success is also old): it is the more actionable signal - the operator needs to
 * know *why* backups stopped, not just that they are old. A missing marker is {@code UNKNOWN}, not
 * {@code STALE}: there is no history to say anything was ever stale, only that nothing is known yet
 * (e.g. a fresh dev checkout with no sidecar running at all).
 */
public final class BackupStateDeriver {

  /** "Older than" the spec's own wording - strictly greater, so exactly 48h is still {@code OK}. */
  static final Duration STALE_AFTER = Duration.ofHours(48);

  private BackupStateDeriver() {}

  public static BackupState deriveState(BackupStatus status, Instant now) {
    if (status == null) {
      return BackupState.UNKNOWN;
    }
    if (status.lastError() != null && !status.lastError().isBlank()) {
      return BackupState.FAILING;
    }
    if (status.lastSuccessAt() == null) {
      return BackupState.STALE;
    }
    if (Duration.between(status.lastSuccessAt(), now).compareTo(STALE_AFTER) > 0) {
      return BackupState.STALE;
    }
    return BackupState.OK;
  }

  /**
   * True when backups exist only on this machine: no bind mount (which the operator would point at
   * a synced folder) and no {@code rclone} remote configured. Independent of {@link #deriveState}:
   * a stack can have {@code OK} backups that still only exist in one place.
   */
  public static boolean isLocalOnly(BackupStatus status) {
    if (status == null) {
      return false;
    }
    boolean hasBindMount = "bind".equalsIgnoreCase(status.targetType());
    return !hasBindMount && !status.remoteConfigured();
  }
}
