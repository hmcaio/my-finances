package com.chm.myfinances.infrastructure.persistence.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link BudgetVersionRepositoryAdapter}, against a real
 * Testcontainers Postgres (ADR 0010) - including the {@code YearMonth}<->first-of-month-{@code
 * date} round trip and the {@code (budget_id, effective_from)} unique constraint from {@code
 * V8__budgets.sql}.
 */
@DatabaseIntegrationTest
class BudgetVersionRepositoryAdapterTest {

  @Autowired private BudgetVersionRepository budgetVersionRepository;
  @Autowired private BudgetRepository budgetRepository;
  @Autowired private CategoryRepository categoryRepository;

  private UUID budgetId;

  @BeforeEach
  void setUp() {
    UUID categoryId =
        TestFixtures.category(categoryRepository, "Groceries Test", CategoryType.EXPENSE).getId();
    budgetId = budgetRepository.save(Budget.create(UUID.randomUUID(), categoryId)).getId();
  }

  @Test
  void savesAndReloadsAVersionRoundTrippingTheYearMonth() {
    BudgetVersion version =
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("500.00"), YearMonth.of(2026, 3));

    budgetVersionRepository.save(version);

    Optional<BudgetVersion> reloaded =
        budgetVersionRepository.findByBudgetIdAndEffectiveFrom(budgetId, YearMonth.of(2026, 3));
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getMonthlyCap()).isEqualByComparingTo("500.00");
    assertThat(reloaded.get().getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void savesAndReloadsATombstoneWithANullCapThenResumesItInPlace() {
    UUID id = UUID.randomUUID();
    budgetVersionRepository.save(BudgetVersion.tombstone(id, budgetId, YearMonth.of(2026, 5)));

    BudgetVersion reloaded =
        budgetVersionRepository
            .findByBudgetIdAndEffectiveFrom(budgetId, YearMonth.of(2026, 5))
            .orElseThrow();
    assertThat(reloaded.isTombstone()).isTrue();
    assertThat(reloaded.getMonthlyCap()).isNull();

    reloaded.updateCap(new BigDecimal("321.00"));
    budgetVersionRepository.save(reloaded);

    BudgetVersion resumed =
        budgetVersionRepository
            .findByBudgetIdAndEffectiveFrom(budgetId, YearMonth.of(2026, 5))
            .orElseThrow();
    assertThat(resumed.isTombstone()).isFalse();
    assertThat(resumed.getMonthlyCap()).isEqualByComparingTo("321.00");
  }

  @Test
  void findByBudgetIdReturnsEveryVersionForThatBudget() {
    budgetVersionRepository.save(
        BudgetVersion.create(UUID.randomUUID(), budgetId, BigDecimal.TEN, YearMonth.of(2026, 1)));
    budgetVersionRepository.save(
        BudgetVersion.create(
            UUID.randomUUID(), budgetId, new BigDecimal("20.00"), YearMonth.of(2026, 3)));

    assertThat(budgetVersionRepository.findByBudgetId(budgetId)).hasSize(2);
  }

  @Test
  void findByBudgetIdAndEffectiveFromOfUnknownMonthReturnsEmpty() {
    assertThat(
            budgetVersionRepository.findByBudgetIdAndEffectiveFrom(budgetId, YearMonth.of(2099, 1)))
        .isEmpty();
  }

  @Test
  void saveOfAnExistingVersionUpdatesRatherThanDuplicating() {
    BudgetVersion version =
        BudgetVersion.create(UUID.randomUUID(), budgetId, BigDecimal.TEN, YearMonth.of(2026, 3));
    budgetVersionRepository.save(version);

    version.updateCap(new BigDecimal("99.00"));
    budgetVersionRepository.save(version);

    assertThat(budgetVersionRepository.findByBudgetId(budgetId)).hasSize(1);
    assertThat(
            budgetVersionRepository
                .findByBudgetIdAndEffectiveFrom(budgetId, YearMonth.of(2026, 3))
                .orElseThrow()
                .getMonthlyCap())
        .isEqualByComparingTo("99.00");
  }
}
