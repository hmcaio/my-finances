package com.chm.myfinances.infrastructure.web.investmentreport;

import com.chm.myfinances.application.investmentreport.DividendTotalByTicker;
import java.math.BigDecimal;
import java.util.UUID;

/** API representation of a {@link DividendTotalByTicker} (F026 spec). */
public record DividendTotalByTickerResponse(UUID productId, String ticker, BigDecimal amount) {

  public static DividendTotalByTickerResponse from(DividendTotalByTicker total) {
    return new DividendTotalByTickerResponse(total.productId(), total.ticker(), total.amount());
  }
}
