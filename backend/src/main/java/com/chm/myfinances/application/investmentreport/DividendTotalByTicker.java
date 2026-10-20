package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.util.UUID;

/** A dividend history total grouped by ticker (F026 spec). */
public record DividendTotalByTicker(UUID productId, String ticker, BigDecimal amount) {}
