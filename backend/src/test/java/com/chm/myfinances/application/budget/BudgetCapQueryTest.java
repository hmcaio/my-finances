package com.chm.myfinances.application.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.testsupport.FakeBudgetVersionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link BudgetCapQuery} - a thin wrapper around {@code
 * BudgetVersionRepository.findByBudgetId} + {@link BudgetVersion#resolveEffective}, tested
 * separately from {@link BudgetVersionTest} to confirm it correctly plugs the repository lookup
 * into the (already domain-unit-tested) resolution rule.
 */
class BudgetCapQueryTest {

  private final FakeBudgetVersionRepository budgetVersionRepository =
      new FakeBudgetVersionRepository();
  private final BudgetCapQuery query = new BudgetCapQuery(budgetVersionRepository);

  private final UUID budgetId = UUID.randomUUID();

  @Test
  void resolvesTheLatestVersionAtOrBeforeTheGivenMonth() {
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("500.00"), YearMonth.of(2026, 1)));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("600.00"), YearMonth.of(2026, 3)));

    Optional<BudgetVersion> effective = query.effectiveCap(budgetId, YearMonth.of(2026, 6));

    assertThat(effective).isPresent();
    assertThat(effective.get().getMonthlyCap()).isEqualByComparingTo("600.00");
  }

  @Test
  void returnsEmptyWhenNoVersionIsEffectiveYet() {
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("500.00"), YearMonth.of(2026, 6)));

    assertThat(query.effectiveCap(budgetId, YearMonth.of(2026, 1))).isEmpty();
  }

  @Test
  void returnsEmptyForABudgetWithNoVersionsAtAll() {
    assertThat(query.effectiveCap(UUID.randomUUID(), YearMonth.of(2026, 1))).isEmpty();
  }
}
