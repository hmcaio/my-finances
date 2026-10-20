package com.chm.myfinances.infrastructure.persistence.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
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
 * ephemeral Postgres via Testcontainers (ADR 0010), so {@code V17} runs for real too, including the
 * {@code UNIQUE (holding_id, date)} constraint (rekeyed from {@code product_id} by F022/ADR 0020).
 * (The other {@code CHECK}s are proven directly in migration tests; the domain refuses a negative
 * balance before it gets here.)
 */
@DatabaseIntegrationTest
class InvestmentSnapshotRepositoryAdapterTest {

  @Autowired private InvestmentSnapshotRepository snapshotRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private EntityManager entityManager;

  private UUID holdingId;
  private UUID otherHoldingId;

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
    UUID productId = saveProduct(categoryId, "Product A Snapshot Repo Test");
    UUID otherProductId = saveProduct(categoryId, "Product B Snapshot Repo Test");
    holdingId = saveHolding(productId, brokerId);
    otherHoldingId = saveHolding(otherProductId, brokerId);
  }

  private UUID saveProduct(UUID categoryId, String name) {
    return productRepository
        .save(InvestmentProduct.create(UUID.randomUUID(), categoryId, null, name, null))
        .getId();
  }

  private UUID saveHolding(UUID productId, UUID accountId) {
    return holdingRepository
        .save(InvestmentHolding.create(UUID.randomUUID(), productId, accountId, null))
        .getId();
  }

  private InvestmentSnapshot snapshot(UUID holding, LocalDate date, String balance) {
    return InvestmentSnapshot.create(UUID.randomUUID(), holding, date, new BigDecimal(balance));
  }

  @Test
  void savesAndReloadsASnapshot() {
    InvestmentSnapshot snapshot = snapshot(holdingId, LocalDate.of(2026, 3, 31), "1234.56");

    snapshotRepository.save(snapshot);
    entityManager.flush();
    entityManager.clear();

    InvestmentSnapshot reloaded =
        snapshotRepository
            .findByHoldingIdAndDate(holdingId, LocalDate.of(2026, 3, 31))
            .orElseThrow();
    assertThat(reloaded.getId()).isEqualTo(snapshot.getId());
    assertThat(reloaded.getHoldingId()).isEqualTo(holdingId);
    assertThat(reloaded.getBalance()).isEqualByComparingTo("1234.56");
  }

  @Test
  void replacingTheBalanceUpdatesTheSameRow() {
    InvestmentSnapshot snapshot = snapshot(holdingId, LocalDate.of(2026, 3, 31), "100.00");
    snapshotRepository.save(snapshot);
    entityManager.flush();

    snapshot.replaceBalance(new BigDecimal("0.00"));
    snapshotRepository.save(snapshot);
    entityManager.flush();
    entityManager.clear();

    List<InvestmentSnapshot> all = snapshotRepository.findByHoldingId(holdingId);
    assertThat(all).hasSize(1);
    assertThat(all.get(0).getBalance()).isEqualByComparingTo("0");
  }

  @Test
  void findByHoldingIdReturnsOnlyThatHoldingsSnapshotsMostRecentFirst() {
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 1, 31), "1.00"));
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 3, 31), "3.00"));
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 2, 28), "2.00"));
    snapshotRepository.save(snapshot(otherHoldingId, LocalDate.of(2026, 2, 28), "9.00"));

    List<InvestmentSnapshot> snapshots = snapshotRepository.findByHoldingId(holdingId);

    assertThat(snapshots)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactly(
            LocalDate.of(2026, 3, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 1, 31));
  }

  @Test
  void findAllOnOrBeforeIncludesTheDateItselfAndExcludesLaterOnes() {
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 1, 31), "1.00"));
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 2, 28), "2.00"));
    snapshotRepository.save(snapshot(otherHoldingId, LocalDate.of(2026, 3, 31), "3.00"));

    List<InvestmentSnapshot> onOrBefore =
        snapshotRepository.findAllOnOrBefore(LocalDate.of(2026, 2, 28));

    assertThat(onOrBefore)
        .extracting(InvestmentSnapshot::getDate)
        .containsExactlyInAnyOrder(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28));
  }

  @Test
  void existsByHoldingIdIsTrueOnlyForAHoldingWithASnapshot() {
    assertThat(snapshotRepository.existsByHoldingId(holdingId)).isFalse();

    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 1, 31), "1.00"));

    assertThat(snapshotRepository.existsByHoldingId(holdingId)).isTrue();
    assertThat(snapshotRepository.existsByHoldingId(otherHoldingId)).isFalse();
  }

  @Test
  void movingTheDateUpdatesTheSameRow() {
    InvestmentSnapshot snapshot = snapshot(holdingId, LocalDate.of(2026, 3, 31), "100.00");
    snapshotRepository.save(snapshot);
    entityManager.flush();
    entityManager.clear();

    InvestmentSnapshot loaded = snapshotRepository.findById(snapshot.getId()).orElseThrow();
    loaded.moveTo(LocalDate.of(2026, 3, 30));
    snapshotRepository.save(loaded);
    entityManager.flush();
    entityManager.clear();

    assertThat(snapshotRepository.findById(snapshot.getId()).orElseThrow().getDate())
        .isEqualTo(LocalDate.of(2026, 3, 30));
    assertThat(snapshotRepository.findByHoldingId(holdingId)).hasSize(1);
  }

  @Test
  void deleteByIdRemovesTheRow() {
    InvestmentSnapshot snapshot =
        snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 3, 31), "1.00"));

    snapshotRepository.deleteById(snapshot.getId());
    entityManager.flush();

    assertThat(snapshotRepository.findById(snapshot.getId())).isEmpty();
  }

  @Test
  void aSecondSnapshotForTheSameHoldingAndDateViolatesTheUniqueConstraint() {
    snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 3, 31), "100.00"));
    entityManager.flush();

    assertThatThrownBy(
            () -> {
              snapshotRepository.save(snapshot(holdingId, LocalDate.of(2026, 3, 31), "200.00"));
              entityManager.flush();
            })
        .isInstanceOfAny(
            DataIntegrityViolationException.class, jakarta.persistence.PersistenceException.class);
  }
}
