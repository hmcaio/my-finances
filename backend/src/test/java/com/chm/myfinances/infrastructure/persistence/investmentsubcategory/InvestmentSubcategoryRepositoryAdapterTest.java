package com.chm.myfinances.infrastructure.persistence.investmentsubcategory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentSubcategoryRepositoryAdapter} against a
 * real Testcontainers Postgres (ADR 0010). Fixtures use a {@code " Test"} suffix: the migration's
 * seeded sub-categories already exist.
 */
@DatabaseIntegrationTest
class InvestmentSubcategoryRepositoryAdapterTest {

  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private EntityManager entityManager;

  private UUID categoryId;
  private UUID otherCategoryId;

  @BeforeEach
  void setUp() {
    categoryId =
        categoryRepository
            .save(InvestmentCategory.reconstitute(UUID.randomUUID(), "Parent Test"))
            .getId();
    otherCategoryId =
        categoryRepository
            .save(InvestmentCategory.reconstitute(UUID.randomUUID(), "Other Parent Test"))
            .getId();
  }

  @Test
  void savesAndReloadsASubcategory() {
    InvestmentSubcategory subcategory =
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), categoryId, "CDB Test");

    subcategoryRepository.save(subcategory);

    Optional<InvestmentSubcategory> reloaded = subcategoryRepository.findById(subcategory.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getName()).isEqualTo("CDB Test");
    assertThat(reloaded.get().getInvestmentCategoryId()).isEqualTo(categoryId);
  }

  @Test
  void savingAgainWithTheSameIdUpdatesTheRowAndKeepsTheParent() {
    // F034/ADR 0028: rename() was deleted (the row is now fixed content) - this still proves
    // save() is an upsert-by-id, re-saving a freshly reconstituted instance for the same id.
    UUID id = UUID.randomUUID();
    subcategoryRepository.save(InvestmentSubcategory.reconstitute(id, categoryId, "Original Test"));

    subcategoryRepository.save(InvestmentSubcategory.reconstitute(id, categoryId, "Renamed Test"));
    entityManager.flush();
    entityManager.clear();

    InvestmentSubcategory reloaded = subcategoryRepository.findById(id).orElseThrow();
    assertThat(reloaded.getName()).isEqualTo("Renamed Test");
    assertThat(reloaded.getInvestmentCategoryId()).isEqualTo(categoryId);
  }

  @Test
  void theSameNameCanLiveUnderTwoCategories() {
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), categoryId, "ETFs Test"));
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), otherCategoryId, "ETFs Test"));

    entityManager.flush();
  }

  @Test
  void theDatabaseRejectsADuplicateNameWithinACategory() {
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), categoryId, "Twice Test"));
    entityManager.flush();

    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), categoryId, "Twice Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_subcategories_category_name");
  }

  @Test
  void theDatabaseRejectsAnUnknownParent() {
    subcategoryRepository.save(
        InvestmentSubcategory.reconstitute(UUID.randomUUID(), UUID.randomUUID(), "Orphan Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("investment_subcategories_investment_category_id_fkey");
  }
}
