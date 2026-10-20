package com.chm.myfinances.application.report;

import com.chm.myfinances.application.investmentreport.AllocationGrouping;
import com.chm.myfinances.application.investmentreport.AllocationRow;
import com.chm.myfinances.application.investmentreport.FiiAllocationBasis;
import com.chm.myfinances.application.investmentreport.FiiAllocationGroupBy;
import com.chm.myfinances.application.investmentreport.FiiAllocationQuery;
import com.chm.myfinances.application.investmentreport.FiiAllocationRow;
import com.chm.myfinances.application.investmentreport.InvestmentAllocationQuery;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Report 9 (F030 spec): two clearly labeled sections, called out separately because their scopes
 * genuinely differ - by institution (every investment holding, any product, {@link
 * AllocationGrouping#INSTITUTION}) and by FII segment (FII-only, {@link FiiAllocationQuery} with
 * {@link FiiAllocationBasis#ACTUAL}/{@link FiiAllocationGroupBy#SEGMENT}) - as of a selectable
 * date/month.
 */
@Service
public class InvestmentAllocationReportGenerator implements ReportGenerator {

  private static final List<String> INSTITUTION_COLUMNS =
      List.of("institution_name", "total_value");
  private static final List<String> SEGMENT_COLUMNS =
      List.of("segment_name", "total_value", "percentage");

  private final InvestmentAllocationQuery investmentAllocationQuery;
  private final FiiAllocationQuery fiiAllocationQuery;
  private final Clock clock;

  public InvestmentAllocationReportGenerator(
      InvestmentAllocationQuery investmentAllocationQuery,
      FiiAllocationQuery fiiAllocationQuery,
      Clock clock) {
    this.investmentAllocationQuery = investmentAllocationQuery;
    this.fiiAllocationQuery = fiiAllocationQuery;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.INVESTMENT_ALLOCATION;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    LocalDate asOf = filter.asOfDate() != null ? filter.asOfDate() : LocalDate.now(clock);
    YearMonth month = filter.month() != null ? filter.month() : YearMonth.now(clock);

    List<List<Object>> institutionRows = new ArrayList<>();
    for (AllocationRow row :
        investmentAllocationQuery.allocation(asOf, AllocationGrouping.INSTITUTION)) {
      institutionRows.add(Arrays.asList(row.institutionName(), row.totalValue()));
    }

    List<List<Object>> segmentRows = new ArrayList<>();
    for (FiiAllocationRow row :
        fiiAllocationQuery.allocation(
            FiiAllocationBasis.ACTUAL, FiiAllocationGroupBy.SEGMENT, month)) {
      segmentRows.add(Arrays.asList(row.label(), row.totalValue(), row.percentage()));
    }

    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section(
                    "BY INSTITUTION", INSTITUTION_COLUMNS, institutionRows),
                new CsvSectionWriter.Section("BY FII SEGMENT", SEGMENT_COLUMNS, segmentRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
