package com.chm.myfinances.application.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.transaction.MonthlySpendByCategoryQuery;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link EmergencyFundSuggestionQuery} (F039, PRD S6.17), written first
 * (ADR 0004): {@code months x} the average monthly expense of the last 12 <em>full</em> months
 * before the current one, skipping the months before the first expense. Fakes only; today is
 * 2026-11-05, so the window is 2025-11 through 2026-10.
 */
class EmergencyFundSuggestionQueryTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final EmergencyFundSuggestionQuery query =
      new EmergencyFundSuggestionQuery(
          new MonthlySpendByCategoryQuery(transactionRepository), TestClocks.fixedAtStartOf(TODAY));

  private void expense(LocalDate date, String amount) {
    transactionRepository.save(
        TransactionMother.expense().withDate(date).withAmount(new BigDecimal(amount)).build());
  }

  @Test
  void averagesTheLastTwelveFullMonthsAndMultipliesByTheRequestedMonths() {
    for (int month = 1; month <= 12; month++) {
      // 2025-11, 2025-12, 2026-01 .. 2026-10 each spend 1000.
      expense(LocalDate.of(2025, 11, 1).plusMonths(month - 1).plusDays(9), "1000.00");
    }

    EmergencyFundSuggestion suggestion = query.suggest(6);

    assertThat(suggestion.months()).isEqualTo(6);
    assertThat(suggestion.monthsAveraged()).isEqualTo(12);
    assertThat(suggestion.averageMonthlyExpense()).isEqualByComparingTo("1000.00");
    assertThat(suggestion.suggestedTarget()).isEqualByComparingTo("6000.00");
  }

  @Test
  void sumsEveryCategoryOfAMonthAndIgnoresTheCurrentMonthAndOlderThanTwelve() {
    expense(LocalDate.of(2026, 10, 10), "600.00");
    expense(LocalDate.of(2026, 10, 20), "400.00");
    expense(LocalDate.of(2026, 11, 2), "99999.00"); // the current month is not a full month
    expense(LocalDate.of(2025, 10, 31), "99999.00"); // thirteen months back is out of the window

    EmergencyFundSuggestion suggestion = query.suggest(3);

    assertThat(suggestion.monthsAveraged()).isEqualTo(1);
    assertThat(suggestion.averageMonthlyExpense()).isEqualByComparingTo("1000.00");
    assertThat(suggestion.suggestedTarget()).isEqualByComparingTo("3000.00");
  }

  @Test
  void skipsLeadingMonthsWithNoExpenseButCountsGapsAfterTheFirstExpense() {
    // First expense in 2026-07; August has none (counts as zero), September and October spend.
    expense(LocalDate.of(2026, 7, 15), "400.00");
    expense(LocalDate.of(2026, 9, 15), "400.00");
    expense(LocalDate.of(2026, 10, 15), "400.00");

    EmergencyFundSuggestion suggestion = query.suggest(6);

    // 1200 over July..October = 4 months -> 300.00 a month.
    assertThat(suggestion.monthsAveraged()).isEqualTo(4);
    assertThat(suggestion.averageMonthlyExpense()).isEqualByComparingTo("300.00");
    assertThat(suggestion.suggestedTarget()).isEqualByComparingTo("1800.00");
  }

  @Test
  void anAveragedMonthlyFigureIsRoundedToCentsBeforeItIsMultiplied() {
    expense(LocalDate.of(2026, 9, 15), "100.00");
    expense(LocalDate.of(2026, 10, 15), "100.01");
    expense(LocalDate.of(2026, 8, 15), "100.00");

    EmergencyFundSuggestion suggestion = query.suggest(6);

    // 300.01 / 3 = 100.0033... -> 100.00; the target is 6 x the displayed average.
    assertThat(suggestion.averageMonthlyExpense()).isEqualByComparingTo("100.00");
    assertThat(suggestion.suggestedTarget()).isEqualByComparingTo("600.00");
  }

  @Test
  void ignoresIncome() {
    transactionRepository.save(
        TransactionMother.income()
            .withDate(LocalDate.of(2026, 10, 5))
            .withAmount(new BigDecimal("8000.00"))
            .build());
    expense(LocalDate.of(2026, 10, 10), "500.00");

    assertThat(query.suggest(2).averageMonthlyExpense()).isEqualByComparingTo("500.00");
  }

  @Test
  void withNoExpenseAtAllTheAverageAndTargetAreZero() {
    EmergencyFundSuggestion suggestion = query.suggest(6);

    assertThat(suggestion.monthsAveraged()).isZero();
    assertThat(suggestion.averageMonthlyExpense()).isEqualByComparingTo("0");
    assertThat(suggestion.suggestedTarget()).isEqualByComparingTo("0");
  }

  @Test
  void monthsOutsideOneToOneHundredTwentyAreRejected() {
    assertThatThrownBy(() -> query.suggest(0)).isInstanceOf(InvalidGoalException.class);
    assertThatThrownBy(() -> query.suggest(121)).isInstanceOf(InvalidGoalException.class);
    assertThat(query.suggest(1).months()).isEqualTo(1);
    assertThat(query.suggest(120).months()).isEqualTo(120);
  }
}
