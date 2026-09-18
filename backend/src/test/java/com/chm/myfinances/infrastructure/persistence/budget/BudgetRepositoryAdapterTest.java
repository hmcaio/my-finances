package com.chm.myfinances.infrastructure.persistence.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link BudgetRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010), so Flyway's {@code V8__budgets.sql} runs for real too,
 * FK/unique constraints included. Same {@code @SpringBootTest} +
 * {@code @Import(TestcontainersConfiguration.class)} + {@code @Transactional} pattern as F005's
 * {@code TransferRepositoryAdapterTest}.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class BudgetRepositoryAdapterTest {

  @Autowired private BudgetRepository budgetRepository;
  @Autowired private CategoryRepository categoryRepository;

  private UUID groceriesCategoryId;

  @BeforeEach
  void setUp() {
    groceriesCategoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Groceries Test", CategoryType.EXPENSE))
            .getId();
  }

  @Test
  void savesAndReloadsABudget() {
    Budget budget = Budget.create(UUID.randomUUID(), groceriesCategoryId);

    budgetRepository.save(budget);

    Optional<Budget> reloaded = budgetRepository.findById(budget.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getCategoryId()).isEqualTo(groceriesCategoryId);
  }

  @Test
  void findAllReturnsEverySavedBudget() {
    budgetRepository.save(Budget.create(UUID.randomUUID(), groceriesCategoryId));

    assertThat(budgetRepository.findAll()).isNotEmpty();
  }

  @Test
  void existsByCategoryIdReflectsPersistedState() {
    assertThat(budgetRepository.existsByCategoryId(groceriesCategoryId)).isFalse();

    budgetRepository.save(Budget.create(UUID.randomUUID(), groceriesCategoryId));

    assertThat(budgetRepository.existsByCategoryId(groceriesCategoryId)).isTrue();
  }

  @Test
  void findByIdOfUnknownIdReturnsEmpty() {
    assertThat(budgetRepository.findById(UUID.randomUUID())).isEmpty();
  }
}
