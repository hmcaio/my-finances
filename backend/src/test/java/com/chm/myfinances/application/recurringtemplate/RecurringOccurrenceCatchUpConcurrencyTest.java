package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.testsupport.TestFixtures;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves catch-up generation is idempotent under concurrency (issue #20): two requests that reach
 * {@link RecurringOccurrenceCatchUpService#runCatchUp()} at the same time - the startup runner
 * overlapping a page load, two browser tabs, React {@code StrictMode}'s double mount in dev - must
 * leave exactly one pending occurrence per {@code (template, due date)}, not one per caller.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} (like {@link
 * RecurringTemplateServiceTransactionalTest}): the race lives between separately-committed
 * repository calls, so a test-wide transaction would hide it (and worker threads wouldn't join it
 * anyway). Because its fixtures are committed, {@link #deleteFixtures()} removes them afterwards -
 * a leftover template would break other tests that count or list templates, and an active one would
 * generate pending rows in their catch-up runs.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecurringOccurrenceCatchUpConcurrencyTest {

  private static final int TRIALS = 5;

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private RecurringOccurrenceCatchUpService catchUpService;
  @Autowired private RecurringTemplateService templateService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PendingRecurringOccurrenceRepository pendingRepository;

  @Autowired private JdbcTemplate jdbc;

  private final List<UUID> createdTemplateIds = new ArrayList<>();
  private final List<UUID> createdAccountIds = new ArrayList<>();
  private final List<UUID> createdCategoryIds = new ArrayList<>();

  /**
   * Hard-deletes everything this test committed (children first). Not just {@code stop}ping the
   * templates: other tests assume the recurring-template tables hold only their own rows (e.g.
   * {@code findAll()} counts), and there is no delete port, so this goes through JDBC.
   */
  @AfterEach
  void deleteFixtures() {
    createdTemplateIds.forEach(
        id -> {
          jdbc.update("DELETE FROM pending_recurring_occurrences WHERE template_id = ?", id);
          jdbc.update("DELETE FROM recurring_template_versions WHERE template_id = ?", id);
          jdbc.update("DELETE FROM recurring_templates WHERE id = ?", id);
        });
    createdAccountIds.forEach(id -> jdbc.update("DELETE FROM accounts WHERE id = ?", id));
    createdCategoryIds.forEach(id -> jdbc.update("DELETE FROM categories WHERE id = ?", id));
    createdTemplateIds.clear();
    createdAccountIds.clear();
    createdCategoryIds.clear();
  }

  @Test
  void twoConcurrentCatchUpRunsGenerateEachCycleOnce() throws Exception {
    for (int trial = 0; trial < TRIALS; trial++) {
      UUID templateId = newTemplateWithBacklog();

      runConcurrently(2, catchUpService::runCatchUp);

      assertOneRowPerDueDate(templateId);
    }
  }

  @Test
  void eightConcurrentCatchUpRunsGenerateEachCycleOnce() throws Exception {
    for (int trial = 0; trial < TRIALS; trial++) {
      UUID templateId = newTemplateWithBacklog();

      runConcurrently(8, catchUpService::runCatchUp);

      assertOneRowPerDueDate(templateId);
    }
  }

  @Test
  void twoConcurrentPendingListRequestsGenerateEachCycleOnce() throws Exception {
    for (int trial = 0; trial < TRIALS; trial++) {
      UUID templateId = newTemplateWithBacklog();

      runConcurrently(2, templateService::findAllPending);

      assertOneRowPerDueDate(templateId);
    }
  }

  /** Three past cycles plus the current month's, so every run has several rows to race over. */
  private UUID newTemplateWithBacklog() {
    String tag = UUID.randomUUID().toString().substring(0, 8);
    UUID categoryId =
        TestFixtures.category(categoryRepository, "Rent Concurrency " + tag, CategoryType.EXPENSE)
            .getId();
    Account account =
        TestFixtures.checkingAccount(
            accountRepository, institutionRepository, "Checking Concurrency " + tag);
    UUID templateId =
        templateService
            .create(
                categoryId,
                account.getId(),
                "Concurrency " + tag,
                new BigDecimal("10.00"),
                1,
                YearMonth.now().minusMonths(3))
            .getId();
    createdCategoryIds.add(categoryId);
    createdAccountIds.add(account.getId());
    createdTemplateIds.add(templateId);
    return templateId;
  }

  private void assertOneRowPerDueDate(UUID templateId) {
    Map<LocalDate, Long> rowsPerDueDate =
        pendingRepository.findByTemplateId(templateId).stream()
            .collect(
                Collectors.groupingBy(
                    PendingRecurringOccurrence::getDueDate, Collectors.counting()));

    assertThat(rowsPerDueDate).hasSize(4);
    assertThat(rowsPerDueDate.values()).containsOnly(1L);
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
