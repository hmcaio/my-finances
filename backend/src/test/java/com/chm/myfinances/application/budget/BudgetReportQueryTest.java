package com.chm.myfinances.application.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.FakeBudgetRepository;
import com.chm.myfinances.testsupport.FakeBudgetVersionRepository;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link BudgetReportQuery} (F006 spec's {@code GET
 * /api/budgets/report}), written first (ADR 0004) against hand-written fakes - plain JUnit, no
 * Spring context. Covers plan.md's "correct cap resolved per month, actual summed across accounts".
 */
class BudgetReportQueryTest {

  private final FakeBudgetRepository budgetRepository = new FakeBudgetRepository();
  private final FakeBudgetVersionRepository budgetVersionRepository =
      new FakeBudgetVersionRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final BudgetCapQuery budgetCapQuery = new BudgetCapQuery(budgetVersionRepository);
  private final BudgetReportQuery reportQuery =
      new BudgetReportQuery(budgetRepository, budgetCapQuery, transactionRepository);

  private final UUID groceriesCategoryId = UUID.randomUUID();
  private final UUID diningCategoryId = UUID.randomUUID();
  private final UUID accountOneId = UUID.randomUUID();
  private final UUID accountTwoId = UUID.randomUUID();
  private final UUID paymentMethodId = UUID.randomUUID();

  @Test
  void resolvesTheHistoricallyCorrectCapAndSumsActualAcrossAccounts() {
    UUID budgetId = UUID.randomUUID();
    budgetRepository.save(Budget.create(budgetId, groceriesCategoryId));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("300.00"), YearMonth.of(2026, 1)));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("400.00"), YearMonth.of(2026, 3)));

    expenseTransaction(groceriesCategoryId, accountOneId, LocalDate.of(2026, 3, 5), "50.00");
    expenseTransaction(groceriesCategoryId, accountTwoId, LocalDate.of(2026, 3, 20), "25.00");
    // Outside the target month/category - must not be counted.
    expenseTransaction(groceriesCategoryId, accountOneId, LocalDate.of(2026, 2, 10), "999.00");
    expenseTransaction(diningCategoryId, accountOneId, LocalDate.of(2026, 3, 10), "999.00");

    List<BudgetReportLine> report = reportQuery.forMonth(YearMonth.of(2026, 3));

    assertThat(report).hasSize(1);
    BudgetReportLine line = report.get(0);
    assertThat(line.categoryId()).isEqualTo(groceriesCategoryId);
    assertThat(line.cap()).isEqualByComparingTo("400.00");
    assertThat(line.actual()).isEqualByComparingTo("75.00");
  }

  @Test
  void usesThePriorMonthsCapWhenNoNewVersionExistsYetForTheTargetMonth() {
    UUID budgetId = UUID.randomUUID();
    budgetRepository.save(Budget.create(budgetId, groceriesCategoryId));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("300.00"), YearMonth.of(2026, 1)));

    List<BudgetReportLine> report = reportQuery.forMonth(YearMonth.of(2026, 6));

    assertThat(report.get(0).cap()).isEqualByComparingTo("300.00");
  }

  @Test
  void capIsNullWhenNoVersionIsEffectiveYetForTheTargetMonth() {
    UUID budgetId = UUID.randomUUID();
    budgetRepository.save(Budget.create(budgetId, groceriesCategoryId));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("300.00"), YearMonth.of(2026, 6)));

    List<BudgetReportLine> report = reportQuery.forMonth(YearMonth.of(2026, 1));

    assertThat(report.get(0).cap()).isNull();
  }

  @Test
  void actualIsZeroWhenThereAreNoTransactionsForTheMonth() {
    UUID budgetId = UUID.randomUUID();
    budgetRepository.save(Budget.create(budgetId, groceriesCategoryId));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("300.00"), YearMonth.of(2026, 1)));

    List<BudgetReportLine> report = reportQuery.forMonth(YearMonth.of(2026, 3));

    assertThat(report.get(0).actual()).isEqualByComparingTo("0.00");
  }

  @Test
  void reportHasOneLinePerBudgetedCategoryOnly() {
    budgetRepository.save(Budget.create(UUID.randomUUID(), groceriesCategoryId));
    budgetRepository.save(Budget.create(UUID.randomUUID(), diningCategoryId));

    List<BudgetReportLine> report = reportQuery.forMonth(YearMonth.of(2026, 3));

    assertThat(report)
        .extracting(BudgetReportLine::categoryId)
        .containsExactlyInAnyOrder(groceriesCategoryId, diningCategoryId);
  }

  private void expenseTransaction(UUID categoryId, UUID accountId, LocalDate date, String amount) {
    transactionRepository.save(
        Transaction.create(
            UUID.randomUUID(),
            date,
            new BigDecimal(amount),
            categoryId,
            CategoryType.EXPENSE,
            accountId,
            paymentMethodId,
            null,
            "Test transaction",
            null));
  }
}
