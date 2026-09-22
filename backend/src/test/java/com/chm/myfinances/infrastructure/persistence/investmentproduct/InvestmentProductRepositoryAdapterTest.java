package com.chm.myfinances.infrastructure.persistence.investmentproduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategory;
import com.chm.myfinances.domain.investmentsubcategory.InvestmentSubcategoryRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.TestFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentProductRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010): round trips, the per-account name uniqueness, the composite
 * foreign key that ties a sub-category to the product's own category (a null sub-category is
 * valid), and the queries behind the account-close and delete guards.
 */
@DatabaseIntegrationTest
class InvestmentProductRepositoryAdapterTest {

  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentSubcategoryRepository subcategoryRepository;
  @Autowired private EntityManager entityManager;

  private UUID accountId;
  private UUID otherAccountId;
  private UUID fixedIncomeId;
  private UUID cryptoId;
  private UUID cdbId;

  @BeforeEach
  void setUp() {
    accountId = saveInvestmentAccount("XP Repo Test");
    otherAccountId = saveInvestmentAccount("Nubank Repo Test");
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

  private UUID saveInvestmentAccount(String name) {
    return TestFixtures.account(
            accountRepository, institutionRepository, name, AccountType.INVESTMENT)
        .getId();
  }

  private InvestmentProduct newProduct(UUID account, UUID subcategory, UUID category, String name) {
    return InvestmentProduct.create(UUID.randomUUID(), account, category, subcategory, name);
  }

  @Test
  void savesAndReloadsAProductWithASubcategory() {
    InvestmentProduct product = newProduct(accountId, cdbId, fixedIncomeId, "CDB 110% Test");

    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    Optional<InvestmentProduct> reloaded = productRepository.findById(product.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getAccountId()).isEqualTo(accountId);
    assertThat(reloaded.get().getInvestmentCategoryId()).isEqualTo(fixedIncomeId);
    assertThat(reloaded.get().getInvestmentSubcategoryId()).isEqualTo(cdbId);
    assertThat(reloaded.get().getName()).isEqualTo("CDB 110% Test");
    assertThat(reloaded.get().isClosed()).isFalse();
  }

  @Test
  void aProductWithoutASubcategoryIsAccepted() {
    InvestmentProduct product = newProduct(accountId, null, cryptoId, "Bitcoin Test");

    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    assertThat(
            productRepository.findById(product.getId()).orElseThrow().getInvestmentSubcategoryId())
        .isNull();
  }

  @Test
  void theCompositeForeignKeyRejectsASubcategoryFromAnotherCategory() {
    // cdbId belongs to fixedIncomeId, not cryptoId.
    productRepository.save(newProduct(accountId, cdbId, cryptoId, "Mismatch Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("fk_investment_products_subcategory_of_category");
  }

  @Test
  void theDatabaseRejectsAnUnknownAccount() {
    productRepository.save(newProduct(UUID.randomUUID(), null, cryptoId, "No Account Test"));
    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("investment_products_account_id_fkey");
  }

  @Test
  void editPersistsClassificationNameAndClosing() {
    InvestmentProduct product =
        productRepository.save(newProduct(accountId, cdbId, fixedIncomeId, "Original Test"));

    product.edit(otherAccountId, cryptoId, null, "Renamed Test");
    product.close(LocalDate.now());
    productRepository.save(product);
    entityManager.flush();
    entityManager.clear();

    InvestmentProduct reloaded = productRepository.findById(product.getId()).orElseThrow();
    assertThat(reloaded.getAccountId()).isEqualTo(otherAccountId);
    assertThat(reloaded.getInvestmentCategoryId()).isEqualTo(cryptoId);
    assertThat(reloaded.getInvestmentSubcategoryId()).isNull();
    assertThat(reloaded.getName()).isEqualTo("Renamed Test");
    assertThat(reloaded.isClosed()).isTrue();
  }

  @Test
  void deleteRemovesTheProduct() {
    InvestmentProduct product =
        productRepository.save(newProduct(accountId, null, cryptoId, "Temp Test"));

    productRepository.deleteById(product.getId());

    assertThat(productRepository.findById(product.getId())).isEmpty();
  }

  @Test
  void findByAccountIdReturnsOnlyThatAccountsProducts() {
    InvestmentProduct atXp =
        productRepository.save(newProduct(accountId, null, cryptoId, "Bitcoin Test"));
    productRepository.save(newProduct(otherAccountId, null, cryptoId, "Bitcoin Test"));

    assertThat(productRepository.findByAccountId(accountId))
        .extracting(InvestmentProduct::getId)
        .containsExactly(atXp.getId());
  }

  @Test
  void nameUniquenessIsScopedToTheAccount() {
    InvestmentProduct selic =
        productRepository.save(newProduct(accountId, null, cryptoId, "Selic Test"));

    assertThat(productRepository.existsByAccountIdAndName(accountId, "Selic Test")).isTrue();
    assertThat(productRepository.existsByAccountIdAndName(otherAccountId, "Selic Test")).isFalse();
    assertThat(
            productRepository.existsByAccountIdAndNameAndIdNot(
                accountId, "Selic Test", selic.getId()))
        .isFalse();
    assertThat(
            productRepository.existsByAccountIdAndNameAndIdNot(
                accountId, "Selic Test", UUID.randomUUID()))
        .isTrue();
  }

  @Test
  void theSameNameIsAcceptedInTwoAccountsButNotTwiceInOne() {
    productRepository.save(newProduct(accountId, null, cryptoId, "Selic Test"));
    productRepository.save(newProduct(otherAccountId, null, cryptoId, "Selic Test"));
    entityManager.flush();

    productRepository.save(newProduct(accountId, null, cryptoId, "Selic Test"));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_products_account_name");
  }

  @Test
  void existsOpenByAccountIdIgnoresClosedProducts() {
    assertThat(productRepository.existsOpenByAccountId(accountId)).isFalse();
    InvestmentProduct product =
        productRepository.save(newProduct(accountId, null, cryptoId, "Bitcoin Test"));
    assertThat(productRepository.existsOpenByAccountId(accountId)).isTrue();
    assertThat(productRepository.existsOpenByAccountId(otherAccountId)).isFalse();

    product.close(LocalDate.now());
    productRepository.save(product);

    assertThat(productRepository.existsOpenByAccountId(accountId)).isFalse();
  }

  @Test
  void existsByCategoryAndSubcategoryBackTheDeleteGuards() {
    assertThat(productRepository.existsByInvestmentCategoryId(fixedIncomeId)).isFalse();
    assertThat(productRepository.existsByInvestmentSubcategoryId(cdbId)).isFalse();

    productRepository.save(newProduct(accountId, cdbId, fixedIncomeId, "CDB Test"));

    assertThat(productRepository.existsByInvestmentCategoryId(fixedIncomeId)).isTrue();
    assertThat(productRepository.existsByInvestmentSubcategoryId(cdbId)).isTrue();
    assertThat(productRepository.existsByInvestmentCategoryId(cryptoId)).isFalse();
  }
}
