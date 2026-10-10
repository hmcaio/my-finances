package com.chm.myfinances.testsupport;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.application.auditlog.AuditLog;
import com.chm.myfinances.domain.budget.BudgetVersionRepository;
import com.chm.myfinances.domain.investmentholding.InvestmentHoldingRepository;
import com.chm.myfinances.domain.investmentsnapshot.InvestmentSnapshotRepository;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Base for every {@code *ServiceTransactionalTest}: proves a service's {@code @Transactional}
 * boundary by stubbing the second write of a use case to throw, so it must carry no
 * {@code @Transactional} of its own (backend {@code CLAUDE.md}, Testing).
 *
 * <p>Declares every spy those tests need in one place. Spring's context cache keys on the set of
 * bean overrides, so five classes each spying a different repository used to build five contexts;
 * with the same spies on all of them they share one. A spy calls through to the real adapter until
 * a test stubs it, and Spring resets every spy after each test (the {@code MockitoSpyBean}
 * default), so a stub never leaks into the next test. A test that stubs a method and needs the real
 * one for cleanup still calls {@code reset(spy)} first.
 *
 * <p>Add a new spy here, not on the subclass: a spy declared on one subclass gives it its own
 * context again.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractTransactionalBoundaryTest {

  @MockitoSpyBean protected PendingRecurringOccurrenceRepository pendingRepository;
  @MockitoSpyBean protected RecurringTemplateVersionRepository versionRepository;
  @MockitoSpyBean protected BudgetVersionRepository budgetVersionRepository;
  @MockitoSpyBean protected InvestmentHoldingRepository holdingRepository;
  @MockitoSpyBean protected InvestmentSnapshotRepository snapshotRepository;

  /**
   * F025 (ADR 0022): proves a use case's {@code @Transactional} boundary also rolls back when the
   * audit insert itself fails, not just when a second business write does.
   */
  @MockitoSpyBean protected AuditLog auditLog;
}
