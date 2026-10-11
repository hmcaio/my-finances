package com.chm.myfinances.infrastructure.web.backupstatus;

import com.chm.myfinances.application.backupstatus.BackupStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST API for the backup status banner (F018 spec's Backend section): {@code GET
 * /api/backup-status}. Always 200 - a missing or unreadable marker is {@code UNKNOWN}, never a
 * 404/500 (dev has no sidecar and no mount at all, which must behave the same as "no marker yet" on
 * a fresh prod stack).
 */
@RestController
@RequestMapping("/api/backup-status")
public class BackupStatusController {

  private final BackupStatusService backupStatusService;

  public BackupStatusController(BackupStatusService backupStatusService) {
    this.backupStatusService = backupStatusService;
  }

  @GetMapping
  public BackupStatusResponse getStatus() {
    return BackupStatusResponse.from(backupStatusService.getStatus());
  }
}
