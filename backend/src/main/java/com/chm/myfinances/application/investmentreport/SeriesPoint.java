package com.chm.myfinances.application.investmentreport;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * One month of a product's value series (F009 spec): {@code value} is the latest snapshot on or
 * before the month-end (or today, for the current month), {@code null} before the first snapshot;
 * {@code contributed} is that month's buys minus sells (cash moved, taxes included); {@code units}
 * the running buys minus sells of {@code quantity}, {@code null} when the product has no recorded
 * quantities. Raw data only - nothing is derived from these.
 */
public record SeriesPoint(
    YearMonth month, BigDecimal value, BigDecimal contributed, BigDecimal units) {}
