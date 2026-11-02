package com.chm.myfinances.infrastructure.persistence.investmentcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-layer integration test for {@link InvestmentCategoryRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010), so {@code V13__investment_accounts_products_taxonomy.sql}
 * runs for real. The migration's seeded categories exist before any test, so fixtures use a {@code
 * " Test"} suffix and nothing asserts the table is empty. The port is read-only (ADR 0028), so rows
 * are inserted with {@link TaxonomySeeder}.
 */
@DatabaseIntegrationTest
class InvestmentCategoryRepositoryAdapterTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvestmentCategoryRepository categoryRepository;

  @Test
  void findsACategoryById() {
    InvestmentCategory category = TaxonomySeeder.investmentCategory(jdbcTemplate, "Bonds Test");

    Optional<InvestmentCategory> reloaded = categoryRepository.findById(category.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("Bonds Test");
    assertThat(categoryRepository.existsById(category.getId())).isTrue();
  }

  @Test
  void theDatabaseRejectsADuplicateName() {
    TaxonomySeeder.investmentCategory(jdbcTemplate, "Twice Test");

    assertThatThrownBy(() -> TaxonomySeeder.investmentCategory(jdbcTemplate, "Twice Test"))
        .hasStackTraceContaining("uq_investment_categories_name");
  }

  @Test
  void theMigrationSeedsTheStarterCategories() {
    assertThat(categoryRepository.findAll())
        .extracting(InvestmentCategory::getName)
        .contains("Fixed Income", "Crypto");
  }
}
