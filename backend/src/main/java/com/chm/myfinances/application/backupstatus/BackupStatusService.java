package com.chm.myfinances.application.backupstatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The one read use case for the backup status banner/endpoint (F018 spec's Backend section): reads
 * the marker through {@link BackupStatusPort}, then derives {@link BackupState} and {@code
 * localOnly} with the pure {@link BackupStateDeriver} - same "one @Service per aggregate" shape as
 * {@code AuditLogService} for a read-only concern with no write use cases of its own (writes come
 * entirely from the sidecar, a separate shell-script codebase, through the mounted file).
 */
@Service
public class BackupStatusService {

  private final BackupStatusPort backupStatusPort;
  private final Clock clock;

  public BackupStatusService(BackupStatusPort backupStatusPort, Clock clock) {
    this.backupStatusPort = backupStatusPort;
    this.clock = clock;
  }

  public BackupStatusReport getStatus() {
    Optional<BackupStatus> status = backupStatusPort.read();
    if (status.isEmpty()) {
      return BackupStatusReport.unknown();
    }
    Instant now = clock.instant();
    BackupState state = BackupStateDeriver.deriveState(status.get(), now);
    boolean localOnly = BackupStateDeriver.isLocalOnly(status.get());
    return BackupStatusReport.of(status.get(), state, localOnly);
  }
}
