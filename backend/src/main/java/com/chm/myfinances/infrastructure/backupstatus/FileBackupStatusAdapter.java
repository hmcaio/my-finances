package com.chm.myfinances.infrastructure.backupstatus;

import com.chm.myfinances.application.backupstatus.BackupStatus;
import com.chm.myfinances.application.backupstatus.BackupStatusPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads the F018 backup sidecar's {@code status.json} marker off disk (spec's Backend section),
 * through the read-only {@code /backups} mount in {@code docker-compose.prod.yml}. When that folder
 * is an in-memory mount (and no rclone remote holds the copy) the marker is reported with {@code
 * lastError = storage_not_persistent}, see {@link VolatileMount}. A missing folder/file or a file
 * that doesn't parse is {@link Optional#empty()}, never an exception - the dev stack has no sidecar
 * and no mount at all, which must behave the same as a prod stack whose sidecar hasn't written its
 * first marker yet.
 *
 * <p>Logs only that the marker could not be read, never its content or the parse exception's own
 * message (which could echo a file-content snippet) - ADR 0011.
 */
@Component
public class FileBackupStatusAdapter implements BackupStatusPort {

  private static final Logger log = LoggerFactory.getLogger(FileBackupStatusAdapter.class);

  // Built locally rather than @Autowired: this app has no general-purpose ObjectMapper bean
  // (docs/conventions/backend-testing.md - the same reason REST-layer tests build their own by
  // hand), so
  // this is the one place a BackupStatus needs deserializing. JavaTimeModule is registered
  // explicitly for the marker's Instant fields - a bare `new ObjectMapper()` can't parse those.
  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final String statusPath;

  private final String mountInfoPath;

  public FileBackupStatusAdapter(
      @Value("${myfinances.backup.status-path}") String statusPath,
      @Value("${myfinances.backup.mountinfo-path:/proc/self/mountinfo}") String mountInfoPath) {
    this.statusPath = statusPath;
    this.mountInfoPath = mountInfoPath;
  }

  private static BackupStatus withError(BackupStatus status, String lastError) {
    return new BackupStatus(
        status.lastSuccessAt(),
        status.lastAttemptAt(),
        lastError,
        status.imageTag(),
        status.buildId(),
        status.schemaVersion(),
        status.targetType(),
        status.remoteConfigured(),
        status.remoteOk(),
        status.count());
  }

  @Override
  public Optional<BackupStatus> read() {
    if (statusPath == null || statusPath.isBlank()) {
      return Optional.empty();
    }
    Path path = Path.of(statusPath);
    if (!Files.isRegularFile(path)) {
      return Optional.empty();
    }
    try {
      String content = Files.readString(path);
      if (content.isBlank()) {
        return Optional.empty();
      }
      BackupStatus status = objectMapper.readValue(content, BackupStatus.class);
      if (!status.remoteConfigured() && VolatileMount.holds(path, Path.of(mountInfoPath))) {
        // The folder this backend reads is an in-memory mount (issue #239): whatever the marker
        // says, it is not what is on the real drive, and a sidecar that has since remounted the
        // real folder is invisible here until the backend restarts too.
        return Optional.of(withError(status, BackupStatus.STORAGE_NOT_PERSISTENT));
      }
      return Optional.of(status);
    } catch (IOException | RuntimeException e) {
      log.warn("backup status marker could not be read: {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }
}
