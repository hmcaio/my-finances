package com.chm.myfinances.application.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.budget.Budget;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.FakeBudgetRepository;
import com.chm.myfinances.testsupport.FakeCategoryRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
  private final CategoryService service =
      new CategoryService(
          repository,
          budgetRepository,
          recurringTemplateRepository,
          transactionRepository,
          idGenerator);

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    CategoryService service =
        new CategoryService(
            repository,
            budgetRepository,
            recurringTemplateRepository,
            transactionRepository,
            new FakeIdGenerator(nextId));

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
        Transaction.create(
            UUID.randomUUID(),
            LocalDate.now(),
            BigDecimal.TEN,
            created.getId(),
            CategoryType.EXPENSE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "In-use transaction",
            null));

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  @Test
  void deleteRejectsACategoryReferencedByABudget() {
    // Post-F007 schema audit: a category with a Budget but zero transactions was previously still
    // hard-deletable, which would have orphaned budgets.category_id's FK.
    Category created = service.create("Groceries", CategoryType.EXPENSE);
    budgetRepository.save(Budget.create(UUID.randomUUID(), created.getId()));

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
        RecurringTemplate.create(UUID.randomUUID(), created.getId(), UUID.randomUUID(), "Rent"));

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }
}
