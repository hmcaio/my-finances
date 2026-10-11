package com.chm.myfinances.application.backupstatus;

import java.util.Optional;

/**
 * The read port for the F018 backup sidecar's status marker (spec's Backend section: "a small read
 * port in application/ and a file-reading adapter in infrastructure/"). {@code
 * infrastructure/backupstatus/FileBackupStatusAdapter} is the only implementation; a missing or
 * unreadable marker is {@link Optional#empty()}, never an exception - there is no table behind
 * this, and no write side (the sidecar, a separate shell-script codebase, owns writing it).
 */
public interface BackupStatusPort {

  Optional<BackupStatus> read();
}
