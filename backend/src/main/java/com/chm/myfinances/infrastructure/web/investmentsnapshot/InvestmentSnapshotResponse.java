package com.chm.myfinances.infrastructure.web.investmentsnapshot;

import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** API representation of an {@link InvestmentSnapshot} (F009 spec). */
public record InvestmentSnapshotResponse(
    UUID id, UUID productId, LocalDate date, BigDecimal balance) {

  public static InvestmentSnapshotResponse from(InvestmentSnapshot snapshot) {
    return new InvestmentSnapshotResponse(
        snapshot.getId(), snapshot.getProductId(), snapshot.getDate(), snapshot.getBalance());
  }
}
