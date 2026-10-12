package com.chm.myfinances.application.report;

import com.chm.myfinances.application.networth.NetWorthGranularity;
import com.chm.myfinances.application.networth.NetWorthPoint;
import com.chm.myfinances.application.networth.NetWorthQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Report 7 (F030 spec): the existing net worth trend series ({@link NetWorthQuery#trend}, F010)
 * over a date range, with the same Monthly/Every-change granularity toggle as the dashboard chart.
 * Defaults mirror {@code NetWorthController}: {@code to} defaults to today, {@code from} to twelve
 * months before {@code to}, granularity to {@code CHANGE_DATE}.
 */
@Service
public class NetWorthTrendReportGenerator implements ReportGenerator {

  private static final int DEFAULT_MONTHS = 12;
  private static final List<String> COLUMNS =
      List.of("date", "net_worth", "assets", "liabilities", "investments");

  private final NetWorthQuery netWorthQuery;
  private final Clock clock;

  public NetWorthTrendReportGenerator(NetWorthQuery netWorthQuery, Clock clock) {
    this.netWorthQuery = netWorthQuery;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.NET_WORTH_TREND;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    LocalDate to = filter.dateTo() != null ? filter.dateTo() : LocalDate.now(clock);
    LocalDate from = filter.dateFrom() != null ? filter.dateFrom() : to.minusMonths(DEFAULT_MONTHS);
    NetWorthGranularity granularity =
        filter.netWorthGranularity() != null
            ? filter.netWorthGranularity()
            : NetWorthGranularity.CHANGE_DATE;

    List<List<Object>> rows = new ArrayList<>();
    for (NetWorthPoint point : netWorthQuery.trend(from, to, granularity)) {
      rows.add(
          List.of(
              point.date(),
              point.netWorth(),
              point.assets(),
              point.liabilities(),
              point.investments()));
    }
    byte[] csv = CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, rows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
