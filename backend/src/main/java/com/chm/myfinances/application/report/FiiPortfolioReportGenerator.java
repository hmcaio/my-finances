package com.chm.myfinances.application.report;

import com.chm.myfinances.application.investmentproduct.InvestmentProductStatus;
import com.chm.myfinances.application.investmentreport.FiiPortfolioQuery;
import com.chm.myfinances.application.investmentreport.FiiPortfolioRow;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Report 3 (F030 spec): the existing FII portfolio list ({@link FiiPortfolioQuery}, F026), as of a
 * selectable month - CSV and PDF, the as-of date resolved the same F009/F010 convention {@code
 * FiiAllocationQuery} already uses for a month filter (month-end, except the current month
 * evaluated at today).
 */
@Service
public class FiiPortfolioReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS =
      List.of(
          "ticker",
          "name",
          "segment_name",
          "cotas_held",
          "amount_contributed",
          "current_value",
          "latest_snapshot_date");

  private final FiiPortfolioQuery portfolioQuery;
  private final InvestmentSegmentRepository segmentRepository;
  private final Clock clock;

  public FiiPortfolioReportGenerator(
      FiiPortfolioQuery portfolioQuery,
      InvestmentSegmentRepository segmentRepository,
      Clock clock) {
    this.portfolioQuery = portfolioQuery;
    this.segmentRepository = segmentRepository;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.FII_PORTFOLIO;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    YearMonth month = filter.month() != null ? filter.month() : YearMonth.now(clock);
    LocalDate asOf = asOfDateFor(month);
    Map<UUID, String> segmentNames = new HashMap<>();
    for (InvestmentSegment segment : segmentRepository.findAll()) {
      segmentNames.put(segment.getId(), segment.getName());
    }

    List<FiiPortfolioRow> rows = portfolioQuery.portfolio(InvestmentProductStatus.ALL, asOf);

    List<List<Object>> dataRows = new ArrayList<>();
    List<List<String>> pdfRows = new ArrayList<>();
    for (FiiPortfolioRow row : rows) {
      String segmentName =
          row.segmentId() == null ? "" : segmentNames.getOrDefault(row.segmentId(), "");
      dataRows.add(
          Arrays.asList(
              row.ticker() == null ? "" : row.ticker(),
              row.name(),
              segmentName,
              row.cotasHeld(),
              row.amountContributed(),
              row.currentValue(),
              row.latestSnapshotDate()));
      pdfRows.add(
          List.of(
              row.ticker() == null ? "" : row.ticker(),
              row.name(),
              segmentName,
              row.cotasHeld().toPlainString(),
              row.amountContributed().toPlainString(),
              row.currentValue().toPlainString(),
              row.latestSnapshotDate() == null ? "" : row.latestSnapshotDate().toString()));
    }

    byte[] csv =
        CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, dataRows)));
    byte[] pdf =
        PdfReportWriter.render(
            "FII Portfolio - " + month,
            List.of(new PdfReportWriter.Section(null, COLUMNS, pdfRows)));

    return List.of(
        new GeneratedFile(type().slug(), ReportFormat.CSV, csv),
        new GeneratedFile(type().slug(), ReportFormat.PDF, pdf));
  }

  /** F009/F010's as-of convention: {@code month}'s last day, except the current month at today. */
  private LocalDate asOfDateFor(YearMonth month) {
    LocalDate today = LocalDate.now(clock);
    LocalDate monthEnd = month.atEndOfMonth();
    return monthEnd.isAfter(today) ? today : monthEnd;
  }
}
