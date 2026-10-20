package com.chm.myfinances.infrastructure.web.networth;

import com.chm.myfinances.application.networth.NetWorthPoint;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point of {@code GET /api/net-worth} and {@code /api/net-worth/trend} (F010 spec). {@code
 * liabilities} is the amount owed on credit cards, a positive number; {@code netWorth = assets +
 * investments - liabilities}.
 */
public record NetWorthPointResponse(
    LocalDate date,
    BigDecimal netWorth,
    BigDecimal assets,
    BigDecimal liabilities,
    BigDecimal investments) {

  public static NetWorthPointResponse from(NetWorthPoint point) {
    return new NetWorthPointResponse(
        point.date(), point.netWorth(), point.assets(), point.liabilities(), point.investments());
  }
}
