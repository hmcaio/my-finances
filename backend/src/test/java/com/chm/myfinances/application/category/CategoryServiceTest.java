package com.chm.myfinances.application.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.IdGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link CategoryService}, written first (ADR 0004) against
 * hand-written fakes for {@link CategoryRepository}/{@link IdGenerator} — plain JUnit, no Spring
 * context, matching how ADR 0004 scopes "pure domain/application logic" tests.
 *
 * <p>Per F002 plan.md, the referenced-by-transaction delete guard (409 when a category is in use)
 * is deferred to F004 (Transactions) — there is nothing to reference yet, so only unconditional
 * create/rename/delete are covered here.
 */
class CategoryServiceTest {

  private final FakeCategoryRepository repository = new FakeCategoryRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final CategoryService service = new CategoryService(repository, idGenerator);

  @Test
  void createAssignsIdFromIdGeneratorAndPersists() {
    UUID nextId = UUID.randomUUID();
    idGenerator.nextId = nextId;

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
    // Unconditional delete for now: F004 (Transactions) doesn't exist yet, so there's no
    // "referenced by a transaction" state to guard against (see F002 plan.md's open item).
    Category created = service.create("Temp", CategoryType.EXPENSE);

    service.delete(created.getId());

    assertThat(repository.findById(created.getId())).isEmpty();
  }

  @Test
  void deleteOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.delete(UUID.randomUUID()))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  private static final class FakeIdGenerator implements IdGenerator {
    // null unless a test pins the next id to assert on it; otherwise generates a fresh one per
    // call, since a fixed value would make every create() collide on the same key.
    private UUID nextId;

    @Override
    public UUID newId() {
      return nextId != null ? nextId : UUID.randomUUID();
    }
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
