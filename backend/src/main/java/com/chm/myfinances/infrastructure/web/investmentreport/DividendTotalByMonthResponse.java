package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.DividendTotalByMonth;
import java.math.BigDecimal;
import java.time.YearMonth;

/** API representation of a {@link DividendTotalByMonth} (F026 spec). */
public record DividendTotalByMonthResponse(YearMonth month, BigDecimal amount) {

  public static DividendTotalByMonthResponse from(DividendTotalByMonth total) {
    return new DividendTotalByMonthResponse(total.month(), total.amount());
  }
}
