package com.chm.myfinances.application.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.budget.BudgetVersion;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.fakes.FakeBudgetRepository;
import com.chm.myfinances.testsupport.fakes.FakeBudgetVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
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

  @Test
  void stopOfUnknownBudgetThrowsNotFound() {
    assertThatThrownBy(() -> service.stop(UUID.randomUUID(), YearMonth.of(2026, 2)))
        .isInstanceOf(BudgetNotFoundException.class);
  }

  @Test
  void stopForANewMonthCreatesATombstoneVersion() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    BudgetVersion tombstone = service.stop(budget.getId(), YearMonth.of(2026, 4));

    assertThat(tombstone.isTombstone()).isTrue();
    assertThat(tombstone.getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 4));
    assertThat(budgetVersionRepository.findByBudgetId(budget.getId())).hasSize(2);
  }

  @Test
  void stopKeepsPastMonthsCappedAndOmitsFutureOnes() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    service.stop(budget.getId(), YearMonth.of(2026, 4));

    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(budget.getId());
    BudgetVersion pastMonth =
        BudgetVersion.resolveEffective(versions, YearMonth.of(2026, 3)).orElseThrow();
    assertThat(pastMonth.isTombstone()).isFalse();
    assertThat(pastMonth.getMonthlyCap()).isEqualByComparingTo("500.00");
    assertThat(isStoppedIn(versions, YearMonth.of(2026, 4))).isTrue();
    assertThat(isStoppedIn(versions, YearMonth.of(2027, 1))).isTrue();
  }

  @Test
  void stopForAnAlreadyVersionedMonthReplacesRatherThanDuplicates() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    BudgetVersion stopped = service.stop(budget.getId(), YearMonth.of(2026, 1));

    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(budget.getId());
    assertThat(versions).hasSize(1);
    assertThat(stopped.isTombstone()).isTrue();
    assertThat(versions.get(0).isTombstone()).isTrue();
  }

  @Test
  void stopWhenAlreadyStoppedIsANoOp() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));
    service.stop(budget.getId(), YearMonth.of(2026, 4));

    service.stop(budget.getId(), YearMonth.of(2026, 6));

    assertThat(budgetVersionRepository.findByBudgetId(budget.getId())).hasSize(2);
  }

  @Test
  void stopThenResumeLeavesTheGapUncapped() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));
    service.stop(budget.getId(), YearMonth.of(2026, 4));

    service.setCap(budget.getId(), new BigDecimal("650.00"), YearMonth.of(2026, 8));

    List<BudgetVersion> versions = budgetVersionRepository.findByBudgetId(budget.getId());
    assertThat(versions).hasSize(3);
    assertThat(
            BudgetVersion.resolveEffective(versions, YearMonth.of(2026, 3))
                .orElseThrow()
                .getMonthlyCap())
        .isEqualByComparingTo("500.00");
    assertThat(isStoppedIn(versions, YearMonth.of(2026, 6))).isTrue();
    assertThat(
            BudgetVersion.resolveEffective(versions, YearMonth.of(2026, 9))
                .orElseThrow()
                .getMonthlyCap())
        .isEqualByComparingTo("650.00");
  }

  @Test
  void setCapInTheStoppedMonthResumesInPlace() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));
    service.stop(budget.getId(), YearMonth.of(2026, 4));

    BudgetVersion resumed =
        service.setCap(budget.getId(), new BigDecimal("700.00"), YearMonth.of(2026, 4));

    assertThat(resumed.isTombstone()).isFalse();
    assertThat(budgetVersionRepository.findByBudgetId(budget.getId())).hasSize(2);
  }

  @Test
  void stopLogsAStoppedLineWithoutTheAmount() {
    Budget budget = service.create(groceriesId, new BigDecimal("612.34"), YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(BudgetService.class)) {
      service.stop(budget.getId(), YearMonth.of(2026, 4));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Budget " + budget.getId() + ": stopped effective 2026-04");
      assertThat(logs.events()).hasSize(1);
    }
  }

  private static boolean isStoppedIn(List<BudgetVersion> versions, YearMonth month) {
    return BudgetVersion.resolveEffective(versions, month).orElseThrow().isTombstone();
  }

  @Test
  void setCapForANewMonthLogsANewVersionLineWithoutTheAmount() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(BudgetService.class)) {
      service.setCap(budget.getId(), new BigDecimal("612.34"), YearMonth.of(2026, 3));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Budget " + budget.getId() + ": new version effective 2026-03");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void setCapForAnAlreadyVersionedMonthLogsAReplacedLineWithoutTheAmount() {
    Budget budget = service.create(groceriesId, new BigDecimal("500.00"), YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(BudgetService.class)) {
      service.setCap(budget.getId(), new BigDecimal("612.34"), YearMonth.of(2026, 1));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Budget " + budget.getId() + ": version effective 2026-01 replaced");
      assertThat(logs.events()).hasSize(1);
    }
  }
}
