package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.reset;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) that {@link
 * AccountService#close}'s {@code @Transactional} boundary rolls back the account-closed write
 * together with the F007 cascade it triggers - the gap flagged in the same follow-up review as
 * {@code RecurringTemplateService.create}/{@code BudgetService.create}: without it, a failure
 * partway through deactivating a closed account's dependent {@code RecurringTemplate}s would leave
 * the account committed closed while some templates stay active, and the caller would see a 500
 * implying the whole close failed when it actually partially succeeded (a retry would then only hit
 * {@link AccountAlreadyClosedException}, never fixing the templates).
 *
 * <p>Deliberately carries no class/method-level {@code @Transactional} - see {@code
 * RecurringTemplateServiceTransactionalTest}'s javadoc for why that would defeat the point being
 * tested here. The {@code Category}/{@code Account}/{@code RecurringTemplate} fixture rows are left
 * in place (no delete port exists for any of them); the {@link PendingRecurringOccurrence} row is
 * cleaned up manually since this class opts out of the rollback-per-test convention.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountServiceTransactionalTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AccountService accountService;
  @Autowired private RecurringTemplateService recurringTemplateService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private RecurringTemplateVersionRepository versionRepository;

  @MockitoSpyBean private PendingRecurringOccurrenceRepository pendingRepository;

  @Test
  void closeRollsBackWhenDeactivatingADependentTemplateFails() {
    UUID categoryId =
        TestFixtures.category(categoryRepository, "Rent Account Close Test", CategoryType.EXPENSE)
            .getId();
    Account account =
        accountService.create(
            "Checking Account Close Test",
            TestInstitutions.builtInId(institutionRepository),
            AccountType.CHECKING,
            BigDecimal.ZERO,
            LocalDate.now());
    RecurringTemplate template =
        recurringTemplateService.create(
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

    willThrow(new RuntimeException("simulated failure deactivating a dependent template"))
        .given(pendingRepository)
        .deleteByTemplateId(template.getId());

    try {
      assertThatThrownBy(() -> accountService.close(account.getId()))
          .isInstanceOf(RuntimeException.class);

      // Both writes must have rolled back together: the account is still open...
      assertThat(accountService.findById(account.getId()).isClosed()).isFalse();
      // ...and its dependent template is still active.
      assertThat(recurringTemplateService.findById(template.getId()).isActive()).isTrue();
    } finally {
      // deleteById was never stubbed in this test, so it still hits the real adapter.
      reset(pendingRepository);
      pendingRepository.deleteById(pending.getId());
    }
  }
}
