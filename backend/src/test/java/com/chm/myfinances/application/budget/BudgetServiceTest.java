package com.chm.myfinances.application.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.FakeBudgetRepository;
import com.chm.myfinances.testsupport.FakeBudgetVersionRepository;
import com.chm.myfinances.testsupport.FakeCategoryRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link BudgetService}, written first (ADR 0004) against hand-written
 * fakes for every repository port it depends on - plain JUnit, no Spring context. Covers F006
 * spec's category-type/uniqueness guards and plan.md's "editing the cap for an already-versioned
 * month replaces that version instead of duplicating it" behavior.
 */
class BudgetServiceTest {

  private final FakeBudgetRepository budgetRepository = new FakeBudgetRepository();
  private final FakeBudgetVersionRepository budgetVersionRepository =
      new FakeBudgetVersionRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final BudgetService service =
      new BudgetService(budgetRepository, budgetVersionRepository, categoryRepository, idGenerator);

  private UUID groceriesId;
  private UUID salaryId;

  @BeforeEach
  void setUp() {
    groceriesId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Groceries", CategoryType.EXPENSE))
            .getId();
    salaryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Salary", CategoryType.INCOME))
            .getId();
  }

  @Test
  void createAssignsIdFromIdGeneratorAndSavesAFirstVersion() {
    UUID nextBudgetId = UUID.randomUUID();
    BudgetService service =
        new BudgetService(
            budgetRepository,
            budgetVersionRepository,
            categoryRepository,
            new FakeIdGenerator(nextBudgetId));

    Budget created = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    assertThat(created.getId()).isEqualTo(nextBudgetId);
    assertThat(created.getCategoryId()).isEqualTo(groceriesId);
    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(created.getId());
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).getMonthlyCap()).isEqualByComparingTo("500.00");
    assertThat(versions.get(0).getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 1));
  }

  @Test
  void createRejectsUnknownCategory() {
    assertThatThrownBy(() -> service.create(UUID.randomUUID(), BigDecimal.TEN, YearMonth.now()))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void createRejectsAnIncomeCategory() {
    assertThatThrownBy(() -> service.create(salaryId, BigDecimal.TEN, YearMonth.now()))
        .isInstanceOf(CategoryNotExpenseException.class);
  }

  @Test
  void createRejectsACategoryThatAlreadyHasABudget() {
    service.create(groceriesId, BigDecimal.TEN, YearMonth.of(2026, 1));

    assertThatThrownBy(() -> service.create(groceriesId, BigDecimal.TEN, YearMonth.of(2026, 2)))
        .isInstanceOf(BudgetAlreadyExistsException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(BudgetNotFoundException.class);
  }

  @Test
  void findAllReturnsEveryBudget() {
    service.create(groceriesId, BigDecimal.TEN, YearMonth.of(2026, 1));

    assertThat(service.findAll()).hasSize(1);
  }

  @Test
  void setCapOfUnknownBudgetThrowsNotFound() {
    assertThatThrownBy(
            () -> service.setCap(UUID.randomUUID(), BigDecimal.TEN, YearMonth.of(2026, 2)))
        .isInstanceOf(BudgetNotFoundException.class);
  }

  @Test
  void setCapForANewMonthCreatesAnAdditionalVersion() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    service.setCap(budget.getId(), new BigDecimal("600.00"), YearMonth.of(2026, 3));

    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(budget.getId());
    assertThat(versions).hasSize(2);
  }

  @Test
  void setCapForAnAlreadyVersionedMonthReplacesRatherThanDuplicates() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    BudgetVersion updated =
        service.setCap(budget.getId(), new BigDecimal("750.00"), YearMonth.of(2026, 1));

    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(budget.getId());
    assertThat(versions).hasSize(1);
    assertThat(updated.getMonthlyCap()).isEqualByComparingTo("750.00");
    assertThat(versions.get(0).getMonthlyCap()).isEqualByComparingTo("750.00");
  }

  @Test
  void priorMonthsKeepTheirOwnCapAfterALaterMonthIsEdited() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    service.setCap(budget.getId(), new BigDecimal("800.00"), YearMonth.of(2026, 3));

    assertThat(
            budgetVersionRepository
                .findByBudgetIdAndEffectiveFrom(budget.getId(), YearMonth.of(2026, 1))
                .orElseThrow()
                .getMonthlyCap())
        .isEqualByComparingTo("500.00");
  }
}
