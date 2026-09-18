package com.chm.myfinances.infrastructure.persistence.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link CategoryRepositoryAdapter}: hits a real, ephemeral
 * Postgres via Testcontainers (ADR 0010) instead of an in-memory substitute, so Flyway's {@code
 * V2__categories_and_payment_methods.sql} runs for real too.
 *
 * <p>Spring Boot 4.x no longer ships {@code @DataJpaTest}/{@code @AutoConfigureTestDatabase} (both
 * test-slice annotations were removed from {@code spring-boot-test-autoconfigure}), so this uses
 * the same full {@code @SpringBootTest} + {@code @Import(TestcontainersConfiguration.class)}
 * pattern as {@code MyFinancesApplicationTests} (F001). {@code @Transactional} rolls back each
 * test's writes so tests don't interfere with each other or with the migration's seed data.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CategoryRepositoryAdapterTest {

  @Autowired private CategoryRepository categoryRepository;

  @Test
  void savesAndReloadsACategory() {
    Category category = Category.create(UUID.randomUUID(), "Groceries Test", CategoryType.EXPENSE);

    categoryRepository.save(category);

    Optional<Category> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Groceries Test");
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.EXPENSE);
  }

  @Test
  void renamePersists() {
    Category category = Category.create(UUID.randomUUID(), "Original", CategoryType.INCOME);
    categoryRepository.save(category);

    category.rename("Renamed");
    categoryRepository.save(category);

    Optional<Category> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Renamed");
    assertThat(reloaded.get().getType()).isEqualTo(CategoryType.INCOME);
  }

  @Test
  void deleteRemovesTheCategory() {
    Category category = Category.create(UUID.randomUUID(), "Temp", CategoryType.EXPENSE);
    categoryRepository.save(category);

    categoryRepository.deleteById(category.getId());

    assertThat(categoryRepository.existsById(category.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    categoryRepository.save(
        Category.create(UUID.randomUUID(), "Unique Name Test", CategoryType.EXPENSE));

    assertThat(categoryRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(categoryRepository.existsByName("unique name test")).isFalse();
    assertThat(categoryRepository.existsByName("Something Else")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    Category category =
        categoryRepository.save(
            Category.create(UUID.randomUUID(), "Exclude Self Test", CategoryType.EXPENSE));

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
}
