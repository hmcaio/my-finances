package com.chm.myfinances.application.report;

import com.chm.myfinances.application.budget.BudgetReportLine;
import com.chm.myfinances.application.budget.BudgetReportQuery;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Report 5 (F030 spec): every category with any spend in a selected month - a budgeted category
 * (per {@link BudgetReportQuery}, tombstones already excluded) shows cap/actual/variance, an
 * unbudgeted expense category with spend shows actual only (cap/variance blank), unioned via {@link
 * TransactionRepository#sumAmountByCategoryAndDateRange} for every other {@code EXPENSE} category
 * {@code BudgetReportQuery} didn't return. CSV and PDF. Single month only (PRD S9: a multi-month
 * range is a future direction).
 */
@Service
public class BudgetsReportGenerator implements ReportGenerator {

  private static final List<String> COLUMNS = List.of("category_name", "cap", "actual", "variance");

  private final BudgetReportQuery budgetReportQuery;
  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final Clock clock;

  public BudgetsReportGenerator(
      BudgetReportQuery budgetReportQuery,
      TransactionRepository transactionRepository,
      CategoryRepository categoryRepository,
      Clock clock) {
    this.budgetReportQuery = budgetReportQuery;
    this.transactionRepository = transactionRepository;
    this.categoryRepository = categoryRepository;
    this.clock = clock;
  }

  @Override
  public ReportType type() {
    return ReportType.BUDGETS_SPENDING;
  }

  @Override
  public List<GeneratedFile> generate(ReportFilter filter) {
    YearMonth month = filter.month() != null ? filter.month() : YearMonth.now(clock);
    LocalDate monthStart = month.atDay(1);
    LocalDate monthEnd = month.atEndOfMonth();

    List<Line> lines = new ArrayList<>();
    Set<java.util.UUID> budgetedCategoryIds = new HashSet<>();
    for (BudgetReportLine line : budgetReportQuery.forMonth(month)) {
      budgetedCategoryIds.add(line.categoryId());
      Category category = categoryRepository.findById(line.categoryId()).orElse(null);
      lines.add(
          new Line(
              category == null ? "" : category.getName(),
              line.cap(),
              line.actual(),
              line.cap() == null ? null : line.cap().subtract(line.actual())));
    }
    for (Category category : categoryRepository.findAll()) {
      if (category.getType() != CategoryType.EXPENSE
          || budgetedCategoryIds.contains(category.getId())) {
        continue;
      }
      BigDecimal actual =
          transactionRepository.sumAmountByCategoryAndDateRange(
              category.getId(), monthStart, monthEnd);
      if (actual.signum() > 0) {
        lines.add(new Line(category.getName(), null, actual, null));
      }
    }
    lines.sort(Comparator.comparing(Line::categoryName));

    List<List<Object>> csvRows = new ArrayList<>();
    List<List<String>> pdfRows = new ArrayList<>();
    for (Line line : lines) {
      csvRows.add(Arrays.asList(line.categoryName(), line.cap(), line.actual(), line.variance()));
      pdfRows.add(
          List.of(
              line.categoryName(),
              line.cap() == null ? "" : line.cap().toPlainString(),
              line.actual().toPlainString(),
              line.variance() == null ? "" : line.variance().toPlainString()));
    }

    byte[] csv =
        CsvSectionWriter.write(List.of(new CsvSectionWriter.Section(null, COLUMNS, csvRows)));
    byte[] pdf =
        PdfReportWriter.render(
            "Budgets & Spending by Category - " + month,
            List.of(new PdfReportWriter.Section(null, COLUMNS, pdfRows)));
    return List.of(
        new GeneratedFile(type().slug(), ReportFormat.CSV, csv),
        new GeneratedFile(type().slug(), ReportFormat.PDF, pdf));
  }

  private record Line(
      String categoryName, BigDecimal cap, BigDecimal actual, BigDecimal variance) {}
}
