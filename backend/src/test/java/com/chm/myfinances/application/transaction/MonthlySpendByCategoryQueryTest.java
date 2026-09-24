package com.chm.myfinances.application.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link MonthlySpendByCategoryQuery} (F012 spec's "sum of expense
 * transactions per category for the current month"), written first (ADR 0004) against the fake
 * repository - plain JUnit, no Spring context.
 */
class MonthlySpendByCategoryQueryTest {

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final MonthlySpendByCategoryQuery query =
      new MonthlySpendByCategoryQuery(transactionRepository);

  private final UUID groceriesId = UUID.randomUUID();
  private final UUID diningId = UUID.randomUUID();

  @Test
  void sumsExpensesPerCategoryLargestFirst() {
    expense(groceriesId, LocalDate.of(2026, 3, 5), "50.00");
    expense(groceriesId, LocalDate.of(2026, 3, 20), "25.00");
    expense(diningId, LocalDate.of(2026, 3, 10), "100.00");

    List<CategorySpend> spend = query.forMonth(YearMonth.of(2026, 3));

    assertThat(spend).extracting(CategorySpend::categoryId).containsExactly(diningId, groceriesId);
    assertThat(spend.get(0).total()).isEqualByComparingTo("100.00");
    assertThat(spend.get(1).total()).isEqualByComparingTo("75.00");
  }

  @Test
  void ignoresOtherMonthsAndIncome() {
    expense(groceriesId, LocalDate.of(2026, 2, 28), "999.00");
    expense(groceriesId, LocalDate.of(2026, 4, 1), "999.00");
    transactionRepository.save(
        TransactionMother.income()
            .withDate(LocalDate.of(2026, 3, 15))
            .withAmount(new BigDecimal("3000.00"))
            .build());
    expense(groceriesId, LocalDate.of(2026, 3, 1), "10.00");
    expense(groceriesId, LocalDate.of(2026, 3, 31), "5.00");

    List<CategorySpend> spend = query.forMonth(YearMonth.of(2026, 3));

    assertThat(spend).hasSize(1);
    assertThat(spend.get(0).total()).isEqualByComparingTo("15.00");
  }

  @Test
  void isEmptyWhenNothingWasSpent() {
    assertThat(query.forMonth(YearMonth.of(2026, 3))).isEmpty();
  }

  private void expense(UUID categoryId, LocalDate date, String amount) {
    transactionRepository.save(
        TransactionMother.expense()
            .withDate(date)
            .withAmount(new BigDecimal(amount))
            .withCategoryId(categoryId)
            .build());
  }
}
