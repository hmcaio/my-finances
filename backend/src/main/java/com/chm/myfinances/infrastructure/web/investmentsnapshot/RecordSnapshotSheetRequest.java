package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Request body for {@code POST /api/investment-snapshot-sheet} (F040): one date for every entry,
 * and at most {@value #MAX_ENTRIES} entries, one per holding. A repeated holding is rejected by
 * {@link #isHoldingIdsUnique()}, which the {@code errors} list reports with a null field (see
 * {@code docs/conventions/errors.md}).
 */
public record RecordSnapshotSheetRequest(
    @NotNull LocalDate date,
    @NotEmpty @Size(max = RecordSnapshotSheetRequest.MAX_ENTRIES)
        List<@Valid SnapshotSheetEntryRequest> entries) {

  public static final int MAX_ENTRIES = 200;

  @JsonIgnore
  @AssertTrue(message = "a holding may appear at most once")
  public boolean isHoldingIdsUnique() {
    if (entries == null) {
      return true;
    }
    Set<UUID> seen = new HashSet<>();
    for (SnapshotSheetEntryRequest entry : entries) {
      if (entry != null && entry.holdingId() != null && !seen.add(entry.holdingId())) {
        return false;
      }
    }
    return true;
  }
}
