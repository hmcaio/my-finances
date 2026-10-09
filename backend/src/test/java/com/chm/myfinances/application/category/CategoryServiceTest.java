package com.chm.myfinances.application.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeBudgetRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.mothers.BudgetMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateMother;
import com.chm.myfinances.testsupport.mothers.TransactionMother;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link CategoryService}, written first (ADR 0004) against
 * hand-written fakes for {@link CategoryRepository}/{@link IdGenerator} — plain JUnit, no Spring
 * context, matching how ADR 0004 scopes "pure domain/application logic" tests.
 *
 * <p>Per F002 plan.md, the referenced-by-transaction delete guard (409 when a category is in use)
 * was deferred until F004 (Transactions) existed to check against — it's exercised here now via
 * {@link FakeTransactionRepository}. The post-F007 schema audit broadened that guard to also check
 * F006's {@link FakeBudgetRepository} and F007's {@link FakeRecurringTemplateRepository}, and added
 * a duplicate-name guard on create/rename.
 */
class CategoryServiceTest {

  private final FakeCategoryRepository repository = new FakeCategoryRepository();
  private final FakeBudgetRepository budgetRepository = new FakeBudgetRepository();
  private final FakeRecurringTemplateRepository recurringTemplateRepository =
      new FakeRecurringTemplateRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final CategoryService service =
      new CategoryService(
          repository,
          budgetRepository,
          recurringTemplateRepository,
          transactionRepository,
          idGenerator,
          new AuditRecorder(auditLog));

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    CategoryService service =
        new CategoryService(
            repository,
            budgetRepository,
            recurringTemplateRepository,
            transactionRepository,
            new FakeIdGenerator(nextId),
            new AuditRecorder(auditLog));

    Category created = service.create("Groceries", CategoryType.EXPENSE);

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Groceries");
    assertThat(created.getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(repository.findById(nextId)).isPresent();
  }

  @Test
  void createRejectsADuplicateName() {
    service.create("Groceries", CategoryType.EXPENSE);

    assertThatThrownBy(() -> service.create("Groceries", CategoryType.INCOME))
        .isInstanceOf(CategoryNameAlreadyExistsException.class);
  }

  @Test
  void findAllReturnsEveryPersistedCategory() {
    service.create("Groceries", CategoryType.EXPENSE);
    service.create("Salary", CategoryType.INCOME);

    List<Category> all = service.findAll();

    assertThat(all).extracting(Category::getName).containsExactlyInAnyOrder("Groceries", "Salary");
  }

  @Test
  void renameUpdatesTheNameOnly() {
    Category created = service.create("Groceries", CategoryType.EXPENSE);

    Category renamed = service.rename(created.getId(), "Groceries & Household");

    assertThat(renamed.getName()).isEqualTo("Groceries & Household");
    assertThat(renamed.getType()).isEqualTo(CategoryType.EXPENSE);
  }

  @Test
  void renameToItsOwnCurrentNameIsAllowed() {
    // The exclude-self check (existsByNameAndIdNot) must not treat a no-op rename as a conflict.
    Category created = service.create("Groceries", CategoryType.EXPENSE);

    Category renamed = service.rename(created.getId(), "Groceries");

    assertThat(renamed.getName()).isEqualTo("Groceries");
  }

  @Test
  void renameRejectsADuplicateName() {
    service.create("Groceries", CategoryType.EXPENSE);
    Category salary = service.create("Salary", CategoryType.INCOME);

    assertThatThrownBy(() -> service.rename(salary.getId(), "Groceries"))
        .isInstanceOf(CategoryNameAlreadyExistsException.class);
  }

  @Test
  void renameOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.rename(UUID.randomUUID(), "New name"))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void deleteRemovesTheCategory() {
    Category created = service.create("Temp", CategoryType.EXPENSE);

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteRejectsABuiltInCategory() {
    Category builtIn =
        repository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Other Expense", CategoryType.EXPENSE, true, false));

    assertThatThrownBy(() -> service.delete(builtIn.getId()))
        .isInstanceOf(BuiltInCategoryException.class);
    assertThat(repository.findById(builtIn.getId())).isPresent();
  }

  @Test
  void deleteOfABuiltInCategoryReportsBuiltInBeforeInUse() {
    Category builtIn =
        repository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Other Income", CategoryType.INCOME, true, false));
    budgetRepository.save(BudgetMother.budget().withCategoryId(builtIn.getId()).build());

    assertThatThrownBy(() -> service.delete(builtIn.getId()))
        .isInstanceOf(BuiltInCategoryException.class);
  }

  @Test
  void renameIsAllowedOnABuiltInCategory() {
    Category builtIn =
        repository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Other Expense", CategoryType.EXPENSE, true, false));

    Category renamed = service.rename(builtIn.getId(), "Diversos");

    assertThat(renamed.getName()).isEqualTo("Diversos");
    assertThat(renamed.isBuiltIn()).isTrue();
  }

  // F024 (ADR 0021): the fuel_category flag independently blocks both delete and rename, unlike
  // built_in (which only blocks delete).

  @Test
  void deleteRejectsTheFuelCategory() {
    Category fuelCategory =
        repository.save(
            Category.reconstitute(UUID.randomUUID(), "Fuel", CategoryType.EXPENSE, false, true));

    assertThatThrownBy(() -> service.delete(fuelCategory.getId()))
        .isInstanceOf(FuelCategoryException.class);
    assertThat(repository.findById(fuelCategory.getId())).isPresent();
  }

  @Test
  void renameRejectsTheFuelCategory() {
    Category fuelCategory =
        repository.save(
            Category.reconstitute(UUID.randomUUID(), "Fuel", CategoryType.EXPENSE, false, true));

    assertThatThrownBy(() -> service.rename(fuelCategory.getId(), "Gas"))
        .isInstanceOf(FuelCategoryException.class);
    assertThat(repository.findById(fuelCategory.getId()).orElseThrow().getName()).isEqualTo("Fuel");
  }

  // F026 (ADR 0023): the dividend_category flag independently blocks both delete and rename, same
  // shape as fuel_category but a separate flag/exception.

  @Test
  void deleteRejectsTheDividendCategory() {
    Category dividendCategory =
        repository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Dividends", CategoryType.INCOME, false, false, true));

    assertThatThrownBy(() -> service.delete(dividendCategory.getId()))
        .isInstanceOf(DividendCategoryException.class);
    assertThat(repository.findById(dividendCategory.getId())).isPresent();
  }

  @Test
  void renameRejectsTheDividendCategory() {
    Category dividendCategory =
        repository.save(
            Category.reconstitute(
                UUID.randomUUID(), "Dividends", CategoryType.INCOME, false, false, true));

    assertThatThrownBy(() -> service.rename(dividendCategory.getId(), "FII Income"))
        .isInstanceOf(DividendCategoryException.class);
    assertThat(repository.findById(dividendCategory.getId()).orElseThrow().getName())
        .isEqualTo("Dividends");
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void deleteRejectsACategoryReferencedByATransaction() {
    // F002 plan.md's deferred delete guard: a category with existing transactions must not be
    // hard-deletable, since that would orphan those transactions' category reference.
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    transactionRepository.save(
        TransactionMother.expense()
            .withCategoryId(created.getId())
            .withAmount(BigDecimal.TEN)
            .withDescription("In-use transaction")
            .build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteRejectsACategoryReferencedByABudget() {
    // Post-F007 schema audit: a category with a Budget but zero transactions was previously still
    // hard-deletable, which would have orphaned budgets.category_id's FK.
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    budgetRepository.save(BudgetMother.budget().withCategoryId(created.getId()).build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteRejectsACategoryReferencedByARecurringTemplate() {
    // Post-F007 schema audit: same reasoning as the Budget case, for
    // recurring_templates.category_id's FK.
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    recurringTemplateRepository.save(
        RecurringTemplateMother.template().withCategoryId(created.getId()).build());

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void createRecordsACreateAuditEntry() {
    Category created = service.create("Groceries", CategoryType.EXPENSE);

    var entry = auditLog.onlyEntry();
    assertThat(entry.entityType())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditEntityType.CATEGORY);
    assertThat(entry.entityId()).isEqualTo(created.getId());
    assertThat(entry.entityLabel()).isEqualTo("Groceries");
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.CREATE);
  }

  @Test
  void renameRecordsAnUpdateAuditEntry() {
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    auditLog.entries().clear();

    service.rename(created.getId(), "Supermarket");

    var entry = auditLog.onlyEntry();
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.UPDATE);
    assertThat(entry.changes()).containsKey("name");
  }

  @Test
  void deleteRecordsADeleteAuditEntry() {
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    auditLog.entries().clear();

    service.delete(created.getId());

    var entry = auditLog.onlyEntry();
    assertThat(entry.action())
        .isEqualTo(com.chm.myfinances.application.auditlog.AuditAction.DELETE);
    assertThat(entry.entityLabel()).isEqualTo("Groceries");
  }
}
