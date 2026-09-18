package com.chm.myfinances.application.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.budget.BudgetRepository;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) that {@link
 * BudgetService#create}'s {@code @Transactional} boundary rolls back both writes together - the gap
 * flagged in the same follow-up review as F007's {@code RecurringTemplateService.create}: a failure
 * saving the first {@code BudgetVersion} could otherwise leave a {@code Budget} with zero versions.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} (unlike every other
 * {@code @SpringBootTest} in this codebase) - see {@code
 * RecurringTemplateServiceTransactionalTest}'s javadoc for why that would defeat the point: it
 * would make the test method itself the outermost transaction, so the service's own
 * {@code @Transactional} would just join it instead of being the independent boundary under test.
 * The {@code Category} fixture row is left in place (no delete port exists for it once created
 * here, and this app's own category delete guard would in any case reject deleting a category with
 * a - rolled back or not - budget reference attempt against it).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BudgetServiceTransactionalTest {

  @Autowired private BudgetService service;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private BudgetRepository budgetRepository;

  @MockitoSpyBean private BudgetVersionRepository budgetVersionRepository;

  @Test
  void createRollsBackTheBudgetWhenSavingItsFirstVersionFails() {
    UUID categoryId =
        categoryRepository
            .save(
                Category.create(
                    UUID.randomUUID(), "Groceries Budget Create Test", CategoryType.EXPENSE))
            .getId();

    willThrow(new RuntimeException("simulated failure saving the first version"))
        .given(budgetVersionRepository)
        .save(any());

    assertThatThrownBy(
            () -> service.create(categoryId, new BigDecimal("500.00"), YearMonth.of(2026, 1)))
        .isInstanceOf(RuntimeException.class);

    // Both writes must have rolled back together: no orphaned budget with zero versions.
    assertThat(budgetRepository.existsByCategoryId(categoryId)).isFalse();
  }
}
