package com.chm.myfinances.application.report;

import java.time.Clock;
import java.time.Year;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Report 11 (F030 spec): one calendar year's total income, total expense, and net worth at the
 * start and end of the year with the delta - deliberately high-level, no per-category breakdown
 * (report 5 already covers that, per month). {@code filter.month()}'s year doubles as the year
 * selector - the spec adds no dedicated year filter field.
 */
@Service
public class AnnualSummaryReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS =
      List.of(
          "year",
          "total_income",
          "total_expense",
          "net_worth_start",
          "net_worth_end",
          "net_worth_delta");

  private final AnnualSummaryQuery annualSummaryQuery;
  private final Clock clock;

  public AnnualSummaryReportGenerator(AnnualSummaryQuery annualSummaryQuery, Clock clock) {
    this.annualSummaryQuery = annualSummaryQuery;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.ANNUAL_SUMMARY;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    Year year = filter.month() != null ? Year.of(filter.month().getYear()) : Year.now(clock);
    AnnualSummary summary = annualSummaryQuery.summarize(year);

    List<Object> row =
        List.of(
            summary.year(),
            summary.totalIncome(),
            summary.totalExpense(),
            summary.netWorthStart(),
            summary.netWorthEnd(),
            summary.netWorthDelta());
    byte[] csv =
        CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, List.of(row))));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
