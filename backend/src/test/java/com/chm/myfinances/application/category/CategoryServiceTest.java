package com.chm.myfinances.application.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link CategoryService}, written first (ADR 0004) against
 * hand-written fakes for {@link CategoryRepository}/{@link IdGenerator} — plain JUnit, no Spring
 * context, matching how ADR 0004 scopes "pure domain/application logic" tests.
 *
 * <p>Per F002 plan.md, the referenced-by-transaction delete guard (409 when a category is in use)
 * was deferred until F004 (Transactions) existed to check against — it's exercised here now via
 * {@link FakeTransactionRepository}.
 */
class CategoryServiceTest {

  private final FakeCategoryRepository repository = new FakeCategoryRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final CategoryService service =
      new CategoryService(repository, transactionRepository, idGenerator);

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    CategoryService service =
        new CategoryService(repository, transactionRepository, new FakeIdGenerator(nextId));

    Category created = service.create("Groceries", CategoryType.EXPENSE);

    assertThat(created.getId()).isEqualTo(nextId);
    assertThat(created.getName()).isEqualTo("Groceries");
    assertThat(created.getType()).isEqualTo(CategoryType.EXPENSE);
    assertThat(repository.findById(nextId)).isPresent();
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
            null));

    assertThatThrownBy(() -> service.delete(created.getId()))
        .isInstanceOf(CategoryInUseException.class);
    assertThat(repository.findById(created.getId())).isPresent();
  }

  private static final class FakeCategoryRepository implements CategoryRepository {
    private final Map<UUID, Category> store = new HashMap<>();

    @Override
    public Category save(Category category) {
      store.put(category.getId(), category);
      return category;
    }

    @Override
    public Optional<Category> findById(UUID id) {
      return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Category> findAll() {
      return List.copyOf(store.values());
    }

    @Override
    public void deleteById(UUID id) {
      store.remove(id);
    }

    @Override
    public boolean existsById(UUID id) {
      return store.containsKey(id);
    }
  }
}
