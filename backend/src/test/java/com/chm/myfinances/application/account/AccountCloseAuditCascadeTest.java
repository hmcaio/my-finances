package com.chm.myfinances.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditLogFilter;
import com.chm.myfinances.application.auditlog.AuditLogRecord;
import com.chm.myfinances.application.auditlog.AuditLogRepository;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.infrastructure.web.RequestLoggingFilter;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import com.chm.myfinances.testsupport.mothers.TestInstitutions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * End-to-end proof (real Spring context + Testcontainers Postgres, ADR 0010) of F025 plan.md's
 * Verification item: "Close an account with an active template: an account CLOSE entry and a SYSTEM
 * template entry appear, sharing one request id." {@code AccountServiceTransactionalTest} already
 * covers this same cascade's rollback behavior with fake-port-level assertions on origin; this test
 * instead goes through the real {@code AuditLogRepositoryAdapter} to prove the one property a fake
 * port can't observe - that both entries really do share the request id the adapter reads from the
 * MDC - by setting the MDC the same way {@code RequestLoggingFilter} would for one HTTP request,
 * then calling {@code AccountService.close()} once.
 */
@DatabaseIntegrationTest
class AccountCloseAuditCascadeTest {

  @Autowired private AccountService accountService;
  @Autowired private RecurringTemplateService recurringTemplateService;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private AuditLogRepository auditLogRepository;

  @AfterEach
  void tearDown() {
    MDC.remove(RequestLoggingFilter.MDC_KEY);
  }

  @Test
  void closingAnAccountWithAnActiveTemplateRecordsBothEntriesUnderTheSameRequestId() {
    String requestId = "test-close-cascade-request-id";
    MDC.put(RequestLoggingFilter.MDC_KEY, requestId);

    UUID categoryId =
        TestFixtures.category(categoryRepository, "Rent Cascade Audit Test", CategoryType.EXPENSE)
            .getId();
    Account account =
        accountService.create(
            "Checking Cascade Audit Test",
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

    accountService.close(account.getId());

    List<AuditLogRecord> accountEntries =
        auditLogRepository
            .findAll(
                new AuditLogFilter(
                    null, null, AuditEntityType.ACCOUNT, AuditAction.CLOSE, null, account.getId()),
                PageRequest.of(0, 10))
            .getContent();
    List<AuditLogRecord> templateEntries =
        auditLogRepository
            .findAll(
                new AuditLogFilter(
                    null,
                    null,
                    AuditEntityType.RECURRING_TEMPLATE,
                    AuditAction.CLOSE,
                    null,
                    template.getId()),
                PageRequest.of(0, 10))
            .getContent();

    assertThat(accountEntries).hasSize(1);
    assertThat(templateEntries).hasSize(1);
    assertThat(accountEntries.get(0).origin()).isEqualTo(AuditOrigin.USER);
    assertThat(templateEntries.get(0).origin()).isEqualTo(AuditOrigin.SYSTEM);
    assertThat(accountEntries.get(0).requestId()).isEqualTo(requestId);
    assertThat(templateEntries.get(0).requestId()).isEqualTo(requestId);
  }
}
