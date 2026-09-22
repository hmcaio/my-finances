package com.chm.myfinances.infrastructure.persistence.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentCategoryRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010), so {@code V13__investment_accounts_products_taxonomy.sql}
 * runs for real. The migration's seeded categories exist before any test, so fixtures use a {@code
 * " Test"} suffix and nothing asserts the table is empty.
 */
@DatabaseIntegrationTest
class InvestmentCategoryRepositoryAdapterTest {

  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private EntityManager entityManager;

  @Test
  void savesAndReloadsACategory() {
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Bonds Test");

    categoryRepository.save(category);

    Optional<InvestmentCategory> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Bonds Test");
  }

  @Test
  void renamePersists() {
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Original Test");
    categoryRepository.save(category);

    category.rename("Renamed Test");
    categoryRepository.save(category);

    assertThat(categoryRepository.findById(category.getId()).orElseThrow().getName())
        .isEqualTo("Renamed Test");
  }

  @Test
  void deleteRemovesTheCategory() {
    InvestmentCategory category = InvestmentCategory.create(UUID.randomUUID(), "Temp Test");
    categoryRepository.save(category);

    categoryRepository.deleteById(category.getId());

    assertThat(categoryRepository.existsById(category.getId())).isFalse();
  }

  @Test
  void existsByNameIsTrueOnlyForAnExactMatch() {
    categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Unique Name Test"));

    assertThat(categoryRepository.existsByName("Unique Name Test")).isTrue();
    assertThat(categoryRepository.existsByName("unique name test")).isFalse();
    assertThat(categoryRepository.existsByName("Something Else Test")).isFalse();
  }

  @Test
  void existsByNameAndIdNotExcludesTheGivenId() {
    InvestmentCategory category =
        categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Exclude Self Test"));

    assertThat(categoryRepository.existsByNameAndIdNot("Exclude Self Test", category.getId()))
        .isFalse();
    assertThat(categoryRepository.existsByNameAndIdNot("Exclude Self Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void theDatabaseRejectsADuplicateNameEvenIfTheServiceCheckIsBypassed() {
    categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Twice Test"));
    entityManager.flush();

    categoryRepository.save(InvestmentCategory.create(UUID.randomUUID(), "Twice Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_categories_name");
  }

  @Test
  void theMigrationSeedsTheStarterCategories() {
    assertThat(categoryRepository.existsByName("Fixed Income")).isTrue();
    assertThat(categoryRepository.existsByName("Crypto")).isTrue();
  }
}
