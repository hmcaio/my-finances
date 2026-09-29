package com.chm.myfinances.infrastructure.persistence.investmentholding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategory;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link InvestmentHoldingRepositoryAdapter} against a real
 * Testcontainers Postgres (ADR 0010, F022/ADR 0020): round trips, the {@code (product_id,
 * account_id)} uniqueness, the account/product foreign keys, and the queries behind the
 * account-close and product/holding delete guards (moved here from the old
 * `InvestmentProductRepositoryAdapterTest`).
 */
@DatabaseIntegrationTest
class InvestmentHoldingRepositoryAdapterTest {

  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private EntityManager entityManager;

  private UUID accountId;
  private UUID otherAccountId;
  private UUID productId;

  @BeforeEach
  void setUp() {
    accountId = saveInvestmentAccount("XP Holding Repo Test");
    otherAccountId = saveInvestmentAccount("Nubank Holding Repo Test");
    UUID categoryId =
        categoryRepository
            .save(InvestmentCategory.create(UUID.randomUUID(), "Fixed Income Holding Repo Test"))
            .getId();
    productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "Selic Holding Repo Test", null))
            .getId();
  }

  private UUID saveInvestmentAccount(String name) {
    return TestFixtures.account(
            accountRepository, institutionRepository, name, AccountType.INVESTMENT)
        .getId();
  }

  private InvestmentHolding newHolding(UUID account) {
    return InvestmentHolding.create(UUID.randomUUID(), productId, account, null);
  }

  @Test
  void savesAndReloadsAHoldingWithNotes() {
    InvestmentHolding holding =
        InvestmentHolding.create(UUID.randomUUID(), productId, accountId, "bought via promo");

    holdingRepository.save(holding);
    entityManager.flush();
    entityManager.clear();

    Optional<InvestmentHolding> reloaded = holdingRepository.findById(holding.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getProductId()).isEqualTo(productId);
    assertThat(reloaded.get().getAccountId()).isEqualTo(accountId);
    assertThat(reloaded.get().getAdditionalNotes()).isEqualTo("bought via promo");
    assertThat(reloaded.get().isClosed()).isFalse();
  }

  @Test
  void theDatabaseRejectsAnUnknownAccount() {
    holdingRepository.save(newHolding(UUID.randomUUID()));

    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("investment_holdings_account_id_fkey");
  }

  @Test
  void editPersistsNotesAndClosing() {
    InvestmentHolding holding = holdingRepository.save(newHolding(accountId));

    holding.editNotes("renamed note");
    holding.close(LocalDate.now());
    holdingRepository.save(holding);
    entityManager.flush();
    entityManager.clear();

    InvestmentHolding reloaded = holdingRepository.findById(holding.getId()).orElseThrow();
    assertThat(reloaded.getAdditionalNotes()).isEqualTo("renamed note");
    assertThat(reloaded.isClosed()).isTrue();
    // productId/accountId stay immutable.
    assertThat(reloaded.getProductId()).isEqualTo(productId);
    assertThat(reloaded.getAccountId()).isEqualTo(accountId);
  }

  @Test
  void deleteRemovesTheHolding() {
    InvestmentHolding holding = holdingRepository.save(newHolding(accountId));

    holdingRepository.deleteById(holding.getId());

    assertThat(holdingRepository.findById(holding.getId())).isEmpty();
  }

  @Test
  void findByAccountIdReturnsOnlyThatAccountsHoldings() {
    InvestmentHolding atXp = holdingRepository.save(newHolding(accountId));
    holdingRepository.save(newHolding(otherAccountId));

    assertThat(holdingRepository.findByAccountId(accountId))
        .extracting(InvestmentHolding::getId)
        .containsExactly(atXp.getId());
  }

  @Test
  void findByProductIdReturnsEveryHoldingOfTheProductAcrossAccounts() {
    InvestmentHolding atXp = holdingRepository.save(newHolding(accountId));
    InvestmentHolding atNu = holdingRepository.save(newHolding(otherAccountId));

    assertThat(holdingRepository.findByProductId(productId))
        .extracting(InvestmentHolding::getId)
        .containsExactlyInAnyOrder(atXp.getId(), atNu.getId());
  }

  @Test
  void findByProductIdAndAccountIdFindsTheExactPair() {
    InvestmentHolding holding = holdingRepository.save(newHolding(accountId));

    assertThat(holdingRepository.findByProductIdAndAccountId(productId, accountId))
        .map(InvestmentHolding::getId)
        .contains(holding.getId());
    assertThat(holdingRepository.findByProductIdAndAccountId(productId, otherAccountId)).isEmpty();
  }

  @Test
  void productAccountPairIsUnique() {
    holdingRepository.save(newHolding(accountId));

    assertThat(holdingRepository.existsByProductIdAndAccountId(productId, accountId)).isTrue();
    assertThat(holdingRepository.existsByProductIdAndAccountId(productId, otherAccountId))
        .isFalse();

    holdingRepository.save(newHolding(accountId));
    assertThatThrownBy(() -> entityManager.flush())
        .hasStackTraceContaining("uq_investment_holdings_product_account");
  }

  @Test
  void existsOpenByAccountIdIgnoresClosedHoldings() {
    assertThat(holdingRepository.existsOpenByAccountId(accountId)).isFalse();
    InvestmentHolding holding = holdingRepository.save(newHolding(accountId));
    assertThat(holdingRepository.existsOpenByAccountId(accountId)).isTrue();
    assertThat(holdingRepository.existsOpenByAccountId(otherAccountId)).isFalse();

    holding.close(LocalDate.now());
    holdingRepository.save(holding);

    assertThat(holdingRepository.existsOpenByAccountId(accountId)).isFalse();
  }

  @Test
  void existsByProductIdBacksTheProductDeleteGuardEvenWhenClosed() {
    assertThat(holdingRepository.existsByProductId(productId)).isFalse();
    InvestmentHolding holding = holdingRepository.save(newHolding(accountId));
    assertThat(holdingRepository.existsByProductId(productId)).isTrue();

    holding.close(LocalDate.now());
    holdingRepository.save(holding);

    assertThat(holdingRepository.existsByProductId(productId)).isTrue();
  }

  @Test
  void existsByAccountIdBacksTheAccountDeleteGuard() {
    assertThat(holdingRepository.existsByAccountId(accountId)).isFalse();
    holdingRepository.save(newHolding(accountId));
    assertThat(holdingRepository.existsByAccountId(accountId)).isTrue();
    assertThat(holdingRepository.existsByAccountId(otherAccountId)).isFalse();
  }
}
