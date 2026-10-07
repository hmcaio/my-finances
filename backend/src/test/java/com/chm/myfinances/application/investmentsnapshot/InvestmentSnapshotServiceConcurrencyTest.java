package com.chm.myfinances.application.investmentsnapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.investmentcategory.InvestmentCategoryRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHolding;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentproduct.InvestmentProduct;
import com.chm.myfinances.domain.investmentproduct.InvestmentProductRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves issue #94's fix: two callers racing {@link InvestmentSnapshotService#record} for the same
 * {@code (holdingId, date)} - the find-then-insert gap means both can see "absent" and both attempt
 * an insert - must leave exactly one snapshot row and surface the loser's failure as the existing
 * 409 {@link InvestmentSnapshotDateTakenException}, not a generic 500 from an uncaught {@code
 * DataIntegrityViolationException}.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} (same reasoning as {@code
 * RecurringOccurrenceCatchUpConcurrencyTest}): the race lives between separately-committed {@code
 * record} calls, each its own transaction via {@code SimpleJpaRepository.save}'s own boundary: a
 * test-wide transaction would hide it, and the worker threads wouldn't join it anyway. Fixtures are
 * committed, so {@link #deleteFixtures()} removes them afterwards.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InvestmentSnapshotServiceConcurrencyTest {

  private static final int TRIALS = 5;

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private InvestmentCategoryRepository categoryRepository;
  @Autowired private InvestmentProductRepository productRepository;
  @Autowired private InvestmentHoldingRepository holdingRepository;
  @Autowired private InvestmentSnapshotService snapshotService;
  @Autowired private JdbcTemplate jdbc;

  private final List<UUID> createdHoldingIds = new ArrayList<>();
  private final List<UUID> createdProductIds = new ArrayList<>();
  private final List<UUID> createdAccountIds = new ArrayList<>();

  @AfterEach
  void deleteFixtures() {
    createdHoldingIds.forEach(
        id -> {
          jdbc.update("DELETE FROM investment_snapshots WHERE holding_id = ?", id);
          jdbc.update("DELETE FROM investment_holdings WHERE id = ?", id);
        });
    createdProductIds.forEach(
        id -> jdbc.update("DELETE FROM investment_products WHERE id = ?", id));
    createdAccountIds.forEach(id -> jdbc.update("DELETE FROM accounts WHERE id = ?", id));
    createdHoldingIds.clear();
    createdProductIds.clear();
    createdAccountIds.clear();
  }

  @Test
  void concurrentRecordCallsForTheSameHoldingAndDateLeaveExactlyOneSnapshotAndNo500()
      throws Exception {
    for (int trial = 0; trial < TRIALS; trial++) {
      UUID holdingId = newHolding();
      LocalDate date = LocalDate.of(2026, 3, 31);

      AtomicInteger conflicts = new AtomicInteger();
      AtomicInteger unexpected = new AtomicInteger();
      runConcurrently(
          4,
          () -> {
            try {
              snapshotService.record(holdingId, date, new BigDecimal("100.00"));
            } catch (InvestmentSnapshotDateTakenException e) {
              conflicts.incrementAndGet();
            } catch (RuntimeException e) {
              unexpected.incrementAndGet();
            }
          });

      assertThat(unexpected.get())
          .as("every losing caller must surface as the 409, never an unmapped exception")
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM investment_snapshots WHERE holding_id = ? AND date = ?",
                  Integer.class,
                  holdingId,
                  date))
          .isEqualTo(1);
    }
  }

  private UUID newHolding() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID brokerId =
        TestFixtures.account(
                accountRepository,
                institutionRepository,
                "Broker Snapshot Race " + tag,
                AccountType.INVESTMENT)
            .getId();
    UUID categoryId = categoryRepository.findAll().get(0).getId();
    UUID productId =
        productRepository
            .save(
                InvestmentProduct.create(
                    UUID.randomUUID(), categoryId, null, "Product Snapshot Race " + tag, null))
            .getId();
    UUID holdingId =
        holdingRepository
            .save(InvestmentHolding.create(UUID.randomUUID(), productId, brokerId, null))
            .getId();
    createdAccountIds.add(brokerId);
    createdProductIds.add(productId);
    createdHoldingIds.add(holdingId);
    return holdingId;
  }

  /** Releases {@code threads} callers at the same instant, so they all read the same state. */
  private void runConcurrently(int threads, Runnable action) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    try {
      CyclicBarrier barrier = new CyclicBarrier(threads);
      List<Callable<Void>> callers = new ArrayList<>();
      for (int i = 0; i < threads; i++) {
        callers.add(
            () -> {
              barrier.await();
              action.run();
              return null;
            });
      }
      for (Future<Void> caller : pool.invokeAll(callers)) {
        caller.get();
      }
    } finally {
      pool.shutdown();
    }
  }
}
