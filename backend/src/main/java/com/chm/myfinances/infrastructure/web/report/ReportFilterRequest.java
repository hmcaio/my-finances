package com.chm.myfinances.infrastructure.web.report;

import com.chm.myfinances.application.networth.NetWorthGranularity;
import com.chm.myfinances.application.report.ReportFilter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * The {@code filter} object of {@code POST /api/reports}' body (F030 spec): every field is
 * optional, and each report type only reads the dimensions it has a use for.
 */
public record ReportFilterRequest(
    LocalDate dateFrom,
    LocalDate dateTo,
    LocalDate asOfDate,
    UUID accountId,
    UUID categoryId,
    UUID vehicleId,
    YearMonth month,
    NetWorthGranularity netWorthGranularity) {

  public ReportFilter toFilter() {
    return new ReportFilter(
        dateFrom, dateTo, asOfDate, accountId, categoryId, vehicleId, month, netWorthGranularity);
  }

  /** {@link ReportFilter#none()} when the request body carries no {@code filter} object at all. */
  public static ReportFilter toFilter(ReportFilterRequest request) {
    return request == null ? ReportFilter.none() : request.toFilter();
  }
}
