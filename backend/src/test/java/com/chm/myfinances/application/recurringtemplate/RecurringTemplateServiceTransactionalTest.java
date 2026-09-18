package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.paymentmethod.PaymentMethodRepository;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) that {@link
 * RecurringTemplateService#confirmPending}/{@link RecurringTemplateService#stop}'s
 * {@code @Transactional} boundary actually rolls back every write in the pair together, not just
 * the last one - the gap flagged during F007 follow-up review: a failure between creating the
 * {@code Transaction}/deactivating the template and deleting the pending row(s) could otherwise
 * leave a pending occurrence confirmable a second time (duplicating the transaction), or a template
 * deactivated while a stale pending occurrence stays attached to it.
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} (unlike every other
 * {@code @SpringBootTest} in this codebase) - that would make the test method itself the outermost
 * transaction, so the service's own {@code @Transactional} would just join it (same physical
 * commit) instead of being the independent boundary under test, and a same-transaction re-read
 * would still see the not-yet-rolled-back writes regardless of what {@code confirmPending}/{@code
 * stop} do. Letting each service call be its own top-level transaction, then re-reading afterwards
 * via fresh repository calls, is what actually proves the rollback happened. {@link
 * PendingRecurringOccurrenceRepository} rows created here are cleaned up manually since this class
 * opts out of the rollback-per-test convention; the {@code Category}/ {@code Account}/{@code
 * RecurringTemplate} fixture rows are left in place (no delete port exists for any of them -
 * accounts/templates are only ever closed/stopped, never deleted, per F003/F007), same as any other
 * fixture data a non-transactional test would accumulate. Each test cleans up its own leftover
 * {@link PendingRecurringOccurrenceRepository} row inline, via whichever of {@code
 * deleteById}/{@code deleteByTemplateId} it did NOT stub to throw (stubbing the other one would
 * make cleanup itself fail).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RecurringTemplateServiceTransactionalTest {

  @Autowired private RecurringTemplateService service;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;
  @Autowired private PaymentMethodRepository paymentMethodRepository;
  @Autowired private RecurringTemplateVersionRepository versionRepository;
  @Autowired private TransactionRepository transactionRepository;

  @MockitoSpyBean private PendingRecurringOccurrenceRepository pendingRepository;

  @Test
  void confirmPendingRollsBackTheCreatedTransactionWhenDeletingThePendingRowFails() {
    UUID categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent", CategoryType.EXPENSE))
            .getId();
    Account account =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Checking",
                null,
                AccountType.CHECKING,
                BigDecimal.ZERO,
                LocalDate.now()));
    UUID paymentMethodId =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Debit")).getId();
    RecurringTemplate template =
        service.create(
            categoryId,
            account.getId(),
            "Rent",
            new BigDecimal("1500.00"),
            5,
            YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    LocalDate dueDate = LocalDate.of(2026, 2, 5);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), dueDate));

    willThrow(new RuntimeException("simulated failure deleting the pending row"))
        .given(pendingRepository)
        .deleteById(pending.getId());

    try {
      assertThatThrownBy(
              () ->
                  service.confirmPending(
                      pending.getId(),
                      new ConfirmOccurrenceOverrides(
                          null, null, null, paymentMethodId, null, null)))
          .isInstanceOf(RuntimeException.class);

      // Both writes must have rolled back together: the pending row was never consumed...
      assertThat(pendingRepository.findById(pending.getId())).isPresent();
      // ...and the Transaction that confirmPending appeared to create never actually committed.
      assertThat(transactionRepository.findByAccountIdOnOrBefore(account.getId(), dueDate))
          .isEmpty();
    } finally {
      // Custom derived delete queries (deleteByTemplateId) aren't self-transactional the way
      // JpaRepository's own deleteById is, so clear the stub and use deleteById here instead of
      // calling deleteByTemplateId with no surrounding transaction.
      reset(pendingRepository);
      pendingRepository.deleteById(pending.getId());
    }
  }

  @Test
  void stopRollsBackDeactivationWhenDeletingPendingOccurrencesFails() {
    UUID categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent", CategoryType.EXPENSE))
            .getId();
    Account account =
        accountRepository.save(
            Account.create(
                UUID.randomUUID(),
                "Checking",
                null,
                AccountType.CHECKING,
                BigDecimal.ZERO,
                LocalDate.now()));
    RecurringTemplate template =
        service.create(
            categoryId,
            account.getId(),
            "Rent",
            new BigDecimal("1500.00"),
            5,
            YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    LocalDate dueDate = LocalDate.of(2026, 2, 5);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), dueDate));

    willThrow(new RuntimeException("simulated failure deleting pending occurrences"))
        .given(pendingRepository)
        .deleteByTemplateId(template.getId());

    try {
      assertThatThrownBy(() -> service.stop(template.getId())).isInstanceOf(RuntimeException.class);

      // Both writes must have rolled back together: the template is still active...
      assertThat(service.findById(template.getId()).isActive()).isTrue();
      // ...and its pending occurrence is still there.
      assertThat(pendingRepository.existsByTemplateIdAndDueDate(template.getId(), dueDate))
          .isTrue();
    } finally {
      // deleteById is self-transactional (a plain JpaRepository method), unlike the stubbed
      // deleteByTemplateId derived query - clearing the stub first anyway for symmetry/safety.
      reset(pendingRepository);
      pendingRepository.deleteById(pending.getId());
    }
  }
}
