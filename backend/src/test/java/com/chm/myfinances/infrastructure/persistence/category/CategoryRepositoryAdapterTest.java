package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.TestFixtures;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link CategoryRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010) instead of an in-memory substitute, so Flyway's {@code
 * V2__categories_and_payment_methods.sql} runs for real too.
 *
 * <p>See backend/CLAUDE.md's Testing section for why this is a full {@code @SpringBootTest} +
 * {@code @Import(TestcontainersConfiguration.class)} rather than {@code @DataJpaTest} (removed in
 * Spring Boot 4.x). {@code @Transactional} rolls back each test's writes so tests don't interfere
 * with each other or with the migration's seed data.
 */
@DatabaseIntegrationTest
class CategoryRepositoryAdapterTest {

  @Autowired private CategoryRepository categoryRepository;

  @Test
  void savesAndReloadsACategory() {
    Category category =
        TestFixtures.category(categoryRepository, "Groceries Test", CategoryType.EXPENSE);

    Optional<Category> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Groceries Test");
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.EXPENSE);
  }

  @Test
  void renamePersists() {
    Category category = TestFixtures.category(categoryRepository, "Original", CategoryType.INCOME);

    category.rename("Renamed");
    categoryRepository.save(category);

    Optional<Category> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.INCOME);
  }

  @Test
  void deleteRemovesTheCategory() {
    Category category = TestFixtures.category(categoryRepository, "Temp", CategoryType.EXPENSE);

    categoryRepository.deleteById(category.getId());

    assertThat(categoryRepository.existsById(category.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    TestFixtures.category(categoryRepository, "Unique Name Test", CategoryType.EXPENSE);

    assertThat(categoryRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(categoryRepository.existsByName("unique name test")).isFalse();
    assertThat(categoryRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Category category =
        TestFixtures.category(categoryRepository, "Exclude Self Test", CategoryType.EXPENSE);

    assertThat(categoryRepository.existsByNameAndIdNot("Exclude Self Test", category.getId()))
        .isFalse();
    assertThat(categoryRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void starterCategoriesFromTheMigrationAreSeeded() {
    List<Category> all = categoryRepository.findAll();

    assertThat(all).extracting(Category::getName).contains("Groceries", "Salary", "Other Income");
    assertThat(all)
        .filteredOn(c -> c.getName().equals("Salary"))
        .extracting(Category::getType)
        .containsExactly(CategoryType.INCOME);
  }

  @Test
  void aNewlySavedCategoryIsNotBuiltIn() {
    Category category =
        TestFixtures.category(categoryRepository, "Not Built In Test", CategoryType.EXPENSE);

    assertThat(categoryRepository.findById(category.getId()).orElseThrow().isBuiltIn()).isFalse();
  }

  @Test
  void migrationSeedsExactlyOneBuiltInCategoryPerType() {
    List<Category> builtIn =
        categoryRepository.findAll().stream().filter(Category::isBuiltIn).toList();

    assertThat(builtIn)
        .extracting(Category::getName, Category::getType)
        .containsExactlyInAnyOrder(
            tuple("Other Expense", CategoryType.EXPENSE),
            tuple("Other Income", CategoryType.INCOME));
  }

  @Test
  void renamingABuiltInCategoryKeepsItBuiltIn() {
    Category builtIn =
        categoryRepository.findAll().stream()
            .filter(c -> c.isBuiltIn() && c.getType() == CategoryType.EXPENSE)
            .findFirst()
            .orElseThrow();

    builtIn.rename("Renamed Built In Test");
    categoryRepository.save(builtIn);

    Category reloaded = categoryRepository.findById(builtIn.getId()).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Renamed Built In Test");
    assertThat(reloaded.isBuiltIn()).isTrue();
  }
}
