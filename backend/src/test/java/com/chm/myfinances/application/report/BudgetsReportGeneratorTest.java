package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.budget.BudgetCapQuery;
import com.chm.myfinances.application.budget.BudgetReportQuery;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeBudgetRepository;
import com.chm.myfinances.testsupport.fakes.FakeBudgetVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.BudgetMother;
import com.chm.myfinances.testsupport.mothers.BudgetVersionMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BudgetsReportGenerator} (F030 spec), written first (ADR 0004): a budgeted
 * category shows cap/actual/variance, an unbudgeted category with spend shows actual only, and a
 * category with neither a budget nor any spend doesn't appear at all.
 */
class BudgetsReportGeneratorTest {

  private static final YearMonth MONTH = YearMonth.of(2026, 6);

  private final FakeBudgetRepository budgetRepository = new FakeBudgetRepository();
  private final FakeBudgetVersionRepository budgetVersionRepository =
      new FakeBudgetVersionRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final BudgetsReportGenerator generator =
      new BudgetsReportGenerator(
          new BudgetReportQuery(
              budgetRepository, new BudgetCapQuery(budgetVersionRepository), transactionRepository),
          transactionRepository,
          categoryRepository,
          TestClocks.fixedAtStartOf(LocalDate.of(2026, 6, 15)));

  private Category expenseCategory(String name) {
    return categoryRepository.save(Category.create(UUID.randomUUID(), name, CategoryType.EXPENSE));
  }

  private void budgetFor(UUID categoryId, String cap) {
    Budget budget = budgetRepository.save(BudgetMother.budget().withCategoryId(categoryId).build());
    budgetVersionRepository.save(
        BudgetVersionMother.version()
            .withBudgetId(budget.getId())
            .withMonthlyCap(new BigDecimal(cap))
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());
  }

  private void spend(UUID categoryId, LocalDate date, String amount) {
    transactionRepository.save(
        TransactionMother.expense()
            .withCategoryId(categoryId)
            .withDate(date)
            .withAmount(new BigDecimal(amount))
            .build());
  }

  @Test
  void aBudgetedCategoryShowsCapActualAndVariance() {
    Category groceries = expenseCategory("Groceries Budget R Test");
    budgetFor(groceries.getId(), "500.00");
    spend(groceries.getId(), LocalDate.of(2026, 6, 10), "300.00");

    List<GeneratedFile> files =
        generator.generate(new ReportFilter(null, null, null, null, null, null, MONTH, null));

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    Map<String, String> row = rows.get(0);
    assertThat(row.get("category_name")).isEqualTo("Groceries Budget R Test");
    assertThat(row.get("cap")).isEqualTo("500.00");
    assertThat(row.get("actual")).isEqualTo("300.00");
    assertThat(row.get("variance")).isEqualTo("200.00");
  }

  @Test
  void anUnbudgetedCategoryWithSpendShowsActualOnlyWithBlankCapAndVariance() {
    Category fun = expenseCategory("Fun Unbudgeted R Test");
    spend(fun.getId(), LocalDate.of(2026, 6, 5), "75.00");

    List<GeneratedFile> files =
        generator.generate(new ReportFilter(null, null, null, null, null, null, MONTH, null));

    Map<String, String> row =
        CsvTestSupport.parseSingleSection(files.get(0).content()).stream()
            .filter(r -> r.get("category_name").equals("Fun Unbudgeted R Test"))
            .findFirst()
            .orElseThrow();
    assertThat(row.get("actual")).isEqualTo("75.00");
    assertThat(row.get("cap")).isEmpty();
    assertThat(row.get("variance")).isEmpty();
  }

  @Test
  void aCategoryWithNoBudgetAndNoSpendDoesNotAppear() {
    expenseCategory("Untouched R Test");

    List<GeneratedFile> files =
        generator.generate(new ReportFilter(null, null, null, null, null, null, MONTH, null));

    assertThat(CsvTestSupport.parseSingleSection(files.get(0).content()))
        .noneMatch(r -> r.get("category_name").equals("Untouched R Test"));
  }

  @Test
  void aBudgetedCategoryWithNoSpendThisMonthStillShowsCapAndZeroActual() {
    Category rent = expenseCategory("Rent Budget R Test");
    budgetFor(rent.getId(), "1500.00");

    List<GeneratedFile> files =
        generator.generate(new ReportFilter(null, null, null, null, null, null, MONTH, null));

    Map<String, String> row =
        CsvTestSupport.parseSingleSection(files.get(0).content()).stream()
            .filter(r -> r.get("category_name").equals("Rent Budget R Test"))
            .findFirst()
            .orElseThrow();
    assertThat(row.get("cap")).isEqualTo("1500.00");
    assertThat(row.get("actual")).isEqualTo("0");
    assertThat(row.get("variance")).isEqualTo("1500.00");
  }

  @Test
  void generatesBothCsvAndPdf() {
    expenseCategory("Solo R Test");

    List<GeneratedFile> files = generator.generate(ReportFilter.none());

    assertThat(files).hasSize(2);
    assertThat(files.get(0).format()).isEqualTo(ReportFormat.CSV);
    assertThat(files.get(1).format()).isEqualTo(ReportFormat.PDF);
  }
}
