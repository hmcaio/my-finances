package com.chm.myfinances.application.report;

import com.chm.myfinances.application.networth.NetWorthGranularity;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Every optional filter dimension a {@link ReportGenerator} might read (F030 spec): one shared
 * shape for all eleven types, each generator reading only the fields it has a use for and ignoring
 * the rest (an irrelevant filter value is never an error). All fields are nullable.
 *
 * <p>{@code month} doubles as the Annual Summary report's year selector ({@link
 * java.time.YearMonth#getYear()}) - the spec deliberately adds no new filter dimension/migration
 * for a full year, and a {@code YearMonth} already carries a year.
 */
public record ReportFilter(
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDate asOfDate,
    UUID accountId,
    UUID categoryId,
    UUID vehicleId,
    YearMonth month,
    NetWorthGranularity netWorthGranularity) {

  /** No filtering at all. */
  public static ReportFilter none() {
    return new ReportFilter(null, null, null, null, null, null, null, null);
  }
}
