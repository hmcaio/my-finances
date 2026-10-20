package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of an {@link InvestmentSnapshot}, rekeyed by holding (F009/F022 spec). */
public record InvestmentSnapshotResponse(
    UUID id, UUID holdingId, LocalDate date, BigDecimal balance) {

  public static InvestmentSnapshotResponse from(InvestmentSnapshot snapshot) {
    return new InvestmentSnapshotResponse(
        snapshot.getId(), snapshot.getHoldingId(), snapshot.getDate(), snapshot.getBalance());
  }
}
