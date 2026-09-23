package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshot;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Persistence-layer integration test for {@link InvestmentSnapshotRepositoryAdapter}: hits a real,
 * ephemeral Postgres via Testcontainers (ADR 0010), so {@code V15} runs for real too, including the
 * {@code UNIQUE (product_id, date)} constraint. (The {@code CHECK}s are proven directly in {@code
 * InvestmentSnapshotsMigrationTest}; the domain refuses a negative balance before it gets here.)
 */
@DatabaseIntegrationTest
class InvestmentSnapshotRepositoryAdapterTest {

  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;

  private UUID productId;
  private UUID otherProductId;

  @BeforeEach
  void setUp() {
    UUID brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Snapshot Repo Test",
                AccountType.INVESTMENT)
            .getId();
    UUID categoryId = categoryRepository.findAll().get(0).getId();
    productId = saveProduct(brokerId, categoryId, "Product A Snapshot Repo Test");
    otherProductId = saveProduct(brokerId, categoryId, "Product B Snapshot Repo Test");
  }

  private UUID saveProduct(UUID brokerId, UUID categoryId, String name) {
    return productRepository
        .save(InvestmentProduct.create(UUID.randomUUID(), brokerId, categoryId, null, name))
        .getId();
  }

  private InvestmentSnapshot snapshot(UUID product, LocalDate date, String balance) {
    return InvestmentSnapshot.create(UUID.randomUUID(), product, date, new BigDecimal(balance));
  }

  @Test
  void savesAndReloadsASnapshot() {
    InvestmentSnapshot snapshot = snapshot(productId, LocalDate.of(2026, 3, 31), "1234.56");

    snapshotRepository.save(snapshot);
    entityManager.flush();
    entityManager.clear();

    InvestmentSnapshot reloaded =
        snapshotRepository
            .findByProductIdAndDate(productId, LocalDate.of(2026, 3, 31))
            .orElseThrow();
    assertThat(reloaded.getId()).isEqualTo(snapshot.getId());
    assertThat(reloaded.getProductId()).isEqualTo(productId);
    assertThat(reloaded.getBalance()).isEqualByComparingTo("1234.56");
  }

  @Test
  void replacingTheBalanceUpdatesTheSameRow() {
    InvestmentSnapshot snapshot = snapshot(productId, LocalDate.of(2026, 3, 31), "100.00");
    snapshotRepository.save(snapshot);
    entityManager.flush();

    snapshot.replaceBalance(new BigDecimal("0.00"));
    snapshotRepository.save(snapshot);
    entityManager.flush();
    entityManager.clear();

    List<InvestmentSnapshot> all = snapshotRepository.findByProductId(productId);
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void findByProductIdReturnsOnlyThatProductsSnapshotsMostRecentFirst() {
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 1, 31), "1.00"));
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 3, 31), "3.00"));
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 2, 28), "2.00"));
    snapshotRepository.save(snapshot(otherProductId, LocalDate.of(2026, 2, 28), "9.00"));

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByProductId(productId);

    assertThat(snapshots)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactly(
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31));
  }

  @Test
  void findAllOnOrBeforeIncludesTheDateItselfAndExcludesLaterOnes() {
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 1, 31), "1.00"));
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 2, 28), "2.00"));
    snapshotRepository.save(snapshot(otherProductId, LocalDate.of(2026, 3, 31), "3.00"));

    List<InvestmentSnapshot> onOrBefore =
        snapshotRepository.findAllOnOrBefore(LocalDate.of(2026, 2, 28));

    assertThat(onOrBefore)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
  }

  @Test
  void existsByProductIdIsTrueOnlyForAProductWithASnapshot() {
    assertThat(snapshotRepository.existsByProductId(productId)).isFalse();

    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 1, 31), "1.00"));

    assertThat(snapshotRepository.existsByProductId(productId)).isTrue();
    assertThat(snapshotRepository.existsByProductId(otherProductId)).isFalse();
  }

  @Test
  void aSecondSnapshotForTheSameProductAndDateViolatesTheUniqueConstraint() {
    snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 3, 31), "100.00"));
    entityManager.flush();

    assertThatThrownBy(
            () -> {
              snapshotRepository.save(snapshot(productId, LocalDate.of(2026, 3, 31), "200.00"));
              entityManager.flush();
            })
        .isInstanceOfAny(
            DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
  }
}
