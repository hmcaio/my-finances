package com.chm.myfinances.infrastructure.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.application.account.AccountService;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end integration test (real Spring context + Testcontainers Postgres, ADR 0010) proving
 * F003's {@code AccountService.close()} actually reaches {@link RealAccountClosedNotifier} and, in
 * turn, {@code RecurringTemplateService.deactivateForAccount} - i.e. that this bean is the one
 * wired up as the {@code AccountClosedNotifier} port's implementation, not a leftover no-op (F007
 * spec, PRD S5.4).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class RealAccountClosedNotifierTest {

  @Autowired private AccountService accountService;
  @Autowired private RecurringTemplateService recurringTemplateService;
  @Autowired private CategoryRepository categoryRepository;

  @Test
  void closingAnAccountDeactivatesEveryRecurringTemplatePointingAtIt() {
    UUID categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent Test", CategoryType.EXPENSE))
            .getId();
    Account account =
        accountService.create(
            "Checking", null, AccountType.CHECKING, BigDecimal.ZERO, LocalDate.now());
    RecurringTemplate template =
        recurringTemplateService.create(
            categoryId, account.getId(), "Rent", new BigDecimal("1500.00"), 5, YearMonth.now());
    assertThat(template.isActive()).isTrue();

    accountService.close(account.getId());

    RecurringTemplate reloaded = recurringTemplateService.findById(template.getId());
    assertThat(reloaded.isActive()).isFalse();
  }
}
