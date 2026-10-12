package com.chm.myfinances.application.report;

import com.chm.myfinances.application.investmentreport.DividendHistoryQuery;
import com.chm.myfinances.application.investmentreport.DividendRow;
import com.chm.myfinances.application.investmentreport.DividendTotalByMonth;
import com.chm.myfinances.application.investmentreport.DividendTotalByTicker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Report 10 (F030 spec): every dividend transaction in a date range, per-product totals with a
 * per-month subtotal - three sections reusing the three {@link DividendHistoryQuery} methods F026
 * already provides.
 */
@Service
public class DividendIncomeReportGenerator implements ReportGenerator {

  private static final List<String> DETAIL_COLUMNS =
      List.of("date", "ticker", "product_name", "amount", "description");
  private static final List<String> TICKER_COLUMNS = List.of("ticker", "total_amount");
  private static final List<String> MONTH_COLUMNS = List.of("month", "total_amount");

  private final DividendHistoryQuery dividendHistoryQuery;

  public DividendIncomeReportGenerator(DividendHistoryQuery dividendHistoryQuery) {
    this.dividendHistoryQuery = dividendHistoryQuery;
  }

  @Override
  public ReportType type() {
    return ReportType.DIVIDEND_INCOME;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    List<List<Object>> detailRows = new ArrayList<>();
    for (DividendRow row :
        dividendHistoryQuery.dividends(null, filter.dateFrom(), filter.dateTo())) {
      detailRows.add(
          Arrays.asList(
              row.date(), row.ticker(), row.productName(), row.amount(), row.description()));
    }

    List<List<Object>> tickerRows = new ArrayList<>();
    for (DividendTotalByTicker total :
        dividendHistoryQuery.totalsByTicker(filter.dateFrom(), filter.dateTo())) {
      tickerRows.add(Arrays.asList(total.ticker(), total.amount()));
    }

    List<List<Object>> monthRows = new ArrayList<>();
    for (DividendTotalByMonth total :
        dividendHistoryQuery.totalsByMonth(filter.dateFrom(), filter.dateTo())) {
      monthRows.add(List.of(total.month(), total.amount()));
    }

    byte[] csv =
        CsvSectionWriter.write(
            List.of(
                new CsvSectionWriter.Section("DIVIDENDS", DETAIL_COLUMNS, detailRows),
                new CsvSectionWriter.Section("TOTALS BY TICKER", TICKER_COLUMNS, tickerRows),
                new CsvSectionWriter.Section("TOTALS BY MONTH", MONTH_COLUMNS, monthRows)));
    return List.of(new GeneratedFile(type().slug(), ReportFormat.CSV, csv));
  }
}
