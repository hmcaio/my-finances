package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.application.investmentsnapshot.RecordedBatch;

/** Result of {@code POST /api/investment-snapshot-sheet}: how many snapshots each way. */
public record RecordSnapshotSheetResponse(int created, int replaced) {

  public static RecordSnapshotSheetResponse from(RecordedBatch batch) {
    return new RecordSnapshotSheetResponse(batch.created(), batch.replaced());
  }
}
