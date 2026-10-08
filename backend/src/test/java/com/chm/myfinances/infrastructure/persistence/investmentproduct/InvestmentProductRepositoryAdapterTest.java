package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegment;
import com.chm.myfinances.domain.investmentsegment.InvestmentSegmentRepository;
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
 * Persistence-layer integration test for {@link InvestmentProductRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010): round trips, the F022/ADR 0020 pure-taxonomy shape (no
 * account/closed-date columns, global name uniqueness), the composite foreign key that ties a
 * sub-category to the product's own category (a null sub-category is valid), and the queries behind
 * the category/sub-category delete guards.
 */
@DatabaseIntegrationTest
class InvestmentProductRepositoryAdapterTest {

  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private InvestmentSegmentRepository segmentRepository;
  @Autowired private EntityManager entityManager;

  private UUID fixedIncomeId;
  private UUID cryptoId;
  private UUID cdbId;

  @BeforeEach
  void setUp() {
    fixedIncomeId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Fixed Income Repo Test"))
            .getId();
    cryptoId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Crypto Repo Test"))
            .getId();
    cdbId =
        subcategoryRepository
            .save(InvestmentSubcategory.create(UUID.randomUUID(), fixedIncomeId, "CDB Repo Test"))
            .getId();
  }

  private InvestmentProduct newProduct(UUID category, UUID subcategory, String name) {
    return InvestmentProduct.create(UUID.randomUUID(), category, subcategory, name, null);
  }

  @Test
  void savesAndReloadsAProductWithASubcategoryAndNotes() {
    InvestmentProduct product =
        InvestmentProduct.create(
            UUID.randomUUID(), fixedIncomeId, cdbId, "CDB 110% Test", "matures 2030");

    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    Optional<InvestmentProduct> reloaded = productRepository.findById(product.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(reloaded.get().getInvestmentSubcategoryId()).isEqualTo(cdbId);
    assertThat(reloaded.get().getName()).isEqualTo("CDB 110% Test");
    assertThat(reloaded.get().getAdditionalNotes()).isEqualTo("matures 2030");
  }

  @Test
  void aProductWithoutASubcategoryOrNotesIsAccepted() {
    InvestmentProduct product = newProduct(cryptoId, null, "Bitcoin Test");

    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    InvestmentProduct reloaded = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloaded.getInvestmentSubcategoryId()).isNull();
    assertThat(reloaded.getAdditionalNotes()).isNull();
  }

  @Test
  void theCompositeForeignKeyRejectsASubcategoryFromAnotherCategory() {
    // cdbId belongs to fixedIncomeId, not cryptoId.
    productRepository.save(newProduct(cryptoId, cdbId, "Mismatch Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("fk_investment_products_subcategory_of_category");
  }

  @Test
  void editPersistsClassificationNameAndNotes() {
    InvestmentProduct product =
        productRepository.save(newProduct(fixedIncomeId, cdbId, "Original Test"));

    product.edit(cryptoId, null, "Renamed Test", "renamed note");
    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    InvestmentProduct reloaded = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloaded.getInvestmentCategoryId()).isEqualTo(cryptoId);
    assertThat(reloaded.getInvestmentSubcategoryId()).isNull();
    assertThat(reloaded.getName()).isEqualTo("Renamed Test");
    assertThat(reloaded.getAdditionalNotes()).isEqualTo("renamed note");
  }

  @Test
  void deleteRemovesTheProduct() {
    InvestmentProduct product = productRepository.save(newProduct(cryptoId, null, "Temp Test"));

    productRepository.deleteById(product.getId());

    assertThat(productRepository.findById(product.getId())).isEmpty();
  }

  @Test
  void nameUniquenessIsGlobal() {
    InvestmentProduct selic = productRepository.save(newProduct(cryptoId, null, "Selic Test"));

    assertThat(productRepository.existsByName("Selic Test")).isTrue();
    assertThat(productRepository.existsByName("Unrelated Test")).isFalse();
    assertThat(productRepository.existsByNameAndIdNot("Selic Test", selic.getId())).isFalse();
    assertThat(productRepository.existsByNameAndIdNot("Selic Test", UUID.randomUUID())).isTrue();
  }

  @Test
  void theSameNameCannotBeUsedTwice() {
    productRepository.save(newProduct(cryptoId, null, "Selic Test"));
    entityManager.flush();

    productRepository.save(newProduct(cryptoId, null, "Selic Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_products_name");
  }

  @Test
  void existsByCategoryAndSubcategoryBackTheDeleteGuards() {
    assertThat(productRepository.existsByInvestmentCategoryId(fixedIncomeId)).isFalse();
    assertThat(productRepository.existsByInvestmentSubcategoryId(cdbId)).isFalse();

    productRepository.save(newProduct(fixedIncomeId, cdbId, "CDB Test"));

    assertThat(productRepository.existsByInvestmentCategoryId(fixedIncomeId)).isTrue();
    assertThat(productRepository.existsByInvestmentSubcategoryId(cdbId)).isTrue();
    assertThat(productRepository.existsByInvestmentCategoryId(cryptoId)).isFalse();
  }

  // F026: optional ticker/segmentId round trip.

  @Test
  void savesAndReloadsTickerAndSegmentId() {
    UUID segmentId =
        segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Shoppings Repo Test")).getId();
    InvestmentProduct product =
        InvestmentProduct.create(
            UUID.randomUUID(), fixedIncomeId, null, "KNRI11 Repo Test", null, "KNRI11", segmentId);

    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    InvestmentProduct reloaded = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloaded.getTicker()).isEqualTo("KNRI11");
    assertThat(reloaded.getSegmentId()).isEqualTo(segmentId);
  }

  @Test
  void tickerAndSegmentIdAreNullWhenNeverSet() {
    InvestmentProduct product = productRepository.save(newProduct(cryptoId, null, "No Ticker Test"));

    InvestmentProduct reloaded = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloaded.getTicker()).isNull();
    assertThat(reloaded.getSegmentId()).isNull();
  }

  @Test
  void existsBySegmentIdBacksTheSegmentDeleteGuard() {
    UUID segmentId =
        segmentRepository.save(InvestmentSegment.create(UUID.randomUUID(), "Logistica Repo Test")).getId();
    assertThat(productRepository.existsBySegmentId(segmentId)).isFalse();

    productRepository.save(
        InvestmentProduct.create(
            UUID.randomUUID(), cryptoId, null, "HGLG11 Repo Test", null, "HGLG11", segmentId));

    assertThat(productRepository.existsBySegmentId(segmentId)).isTrue();
  }
}
