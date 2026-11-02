package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TaxonomySeeder;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Persistence-layer integration test for {@link InvestmentSubcategoryRepositoryAdapter} against a
 * real Testcontainers Postgres (ADR 0010). Fixtures use a {@code " Test"} suffix: the migration's
 * seeded sub-categories already exist. The port is read-only (ADR 0028), so rows are inserted with
 * {@link TaxonomySeeder}.
 */
@DatabaseIntegrationTest
class InvestmentSubcategoryRepositoryAdapterTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;

  private UUID categoryId;
  private UUID otherCategoryId;

  @BeforeEach
  void setUp() {
    categoryId = TaxonomySeeder.investmentCategory(jdbcTemplate, "Parent Test").getId();
    otherCategoryId = TaxonomySeeder.investmentCategory(jdbcTemplate, "Other Parent Test").getId();
  }

  @Test
  void findsASubcategoryByIdWithItsParent() {
    InvestmentSubcategory subcategory =
        TaxonomySeeder.investmentSubcategory(jdbcTemplate, categoryId, "CDB Test");

    Optional<InvestmentSubcategory> reloaded = subcategoryRepository.findById(subcategory.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("CDB Test");
    assertThat(reloaded.get().getInvestmentCategoryId()).isEqualTo(categoryId);
  }

  @Test
  void theMigrationSeedsTheStarterSubcategories() {
    assertThat(subcategoryRepository.findAll())
        .extracting(InvestmentSubcategory::getName)
        .contains("CDB");
  }

  @Test
  void theSameNameCanLiveUnderTwoCategories() {
    TaxonomySeeder.investmentSubcategory(jdbcTemplate, categoryId, "ETFs Test");
    TaxonomySeeder.investmentSubcategory(jdbcTemplate, otherCategoryId, "ETFs Test");

    assertThat(subcategoryRepository.findAll())
        .filteredOn(s -> s.getName().equals("ETFs Test"))
        .hasSize(2);
  }

  @Test
  void theDatabaseRejectsADuplicateNameWithinACategory() {
    TaxonomySeeder.investmentSubcategory(jdbcTemplate, categoryId, "Twice Test");

    assertThatThrownBy(
            () -> TaxonomySeeder.investmentSubcategory(jdbcTemplate, categoryId, "Twice Test"))
        .hasStackTraceContaining("uq_investment_subcategories_category_name");
  }

  @Test
  void theDatabaseRejectsAnUnknownParent() {
    assertThatThrownBy(
            () ->
                TaxonomySeeder.investmentSubcategory(
                    jdbcTemplate, UUID.randomUUID(), "Orphan Test"))
        .hasStackTraceContaining("investment_subcategories_investment_category_id_fkey");
  }
}
