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
    InvestmentCategory category = InvestmentCategory.reconstitute(UUID.randomUUID(), "Bonds Test");

    categoryRepository.save(category);

    Optional<InvestmentCategory> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Bonds Test");
  }

  @Test
  void savingAgainWithTheSameIdUpdatesTheRow() {
    // F034/ADR 0028: rename() was deleted (the row is now fixed content) - this still proves
    // save() is an upsert-by-id, re-saving a freshly reconstituted instance for the same id.
    UUID id = UUID.randomUUID();
    categoryRepository.save(InvestmentCategory.reconstitute(id, "Original Test"));

    categoryRepository.save(InvestmentCategory.reconstitute(id, "Renamed Test"));

    assertThat(categoryRepository.findById(id).orElseThrow().getName()).isEqualTo("Renamed Test");
  }

  @Test
  void theDatabaseRejectsADuplicateNameEvenIfTheServiceCheckIsBypassed() {
    categoryRepository.save(InvestmentCategory.reconstitute(UUID.randomUUID(), "Twice Test"));
    entityManager.flush();

    categoryRepository.save(InvestmentCategory.reconstitute(UUID.randomUUID(), "Twice Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_categories_name");
  }

  @Test
  void theMigrationSeedsTheStarterCategories() {
    assertThat(categoryRepository.findAll())
        .extracting(InvestmentCategory::getName)
        .contains("Fixed Income", "Crypto");
  }
}
