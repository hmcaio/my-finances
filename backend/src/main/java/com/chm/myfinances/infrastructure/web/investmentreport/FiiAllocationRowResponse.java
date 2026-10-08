package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.FiiAllocationRow;
import java.math.BigDecimal;
import java.util.UUID;

/** API representation of a {@link FiiAllocationRow} (F026 spec). */
public record FiiAllocationRowResponse(
    UUID key, String label, BigDecimal totalValue, BigDecimal percentage) {

  public static FiiAllocationRowResponse from(FiiAllocationRow row) {
    return new FiiAllocationRowResponse(row.key(), row.label(), row.totalValue(), row.percentage());
  }
}
