package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.time.YearMonth;

/** A dividend history total grouped by month (F026 spec). */
public record DividendTotalByMonth(YearMonth month, BigDecimal amount) {}
