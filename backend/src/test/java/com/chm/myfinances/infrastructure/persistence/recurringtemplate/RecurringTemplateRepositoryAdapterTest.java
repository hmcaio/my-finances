package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.TestInstitutions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link RecurringTemplateRepositoryAdapter}, against a real
 * Testcontainers Postgres (ADR 0010) - including the {@code lastGeneratedFor} {@code
 * YearMonth}<->first-of-month-{@code date} round trip.
 */
@DatabaseIntegrationTest
class RecurringTemplateRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private RecurringTemplateRepository templateRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;

  private UUID categoryId;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent Test", CategoryType.EXPENSE))
            .getId();
    accountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    TestInstitutions.builtInId(institutionRepository),
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
  }

  @Test
  void savesAndReloadsATemplate() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent");

    templateRepository.save(template);

    Optional<RecurringTemplate> reloaded = templateRepository.findById(template.getId());
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getDescription()).isEqualTo("Rent");
    assertThat(reloaded.get().isActive()).isTrue();
    assertThat(reloaded.get().getLastGeneratedFor()).isNull();
  }

  @Test
  void roundTripsLastGeneratedForAsAYearMonth() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent");
    template.advanceLastGeneratedFor(YearMonth.of(2026, 4));
    templateRepository.save(template);

    RecurringTemplate reloaded = templateRepository.findById(template.getId()).orElseThrow();

    assertThat(reloaded.getLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 4));
  }

  @Test
  void findAllActiveExcludesStoppedTemplates() {
    RecurringTemplate active =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Active"));
    RecurringTemplate stopped =
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Stopped");
    stopped.close();
    templateRepository.save(stopped);

    assertThat(templateRepository.findAllActive())
        .extracting(RecurringTemplate::getId)
        .containsExactly(active.getId());
  }

  @Test
  void findByAccountIdReturnsOnlyTemplatesForThatAccount() {
    UUID otherAccountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Savings",
                    TestInstitutions.builtInId(institutionRepository),
                    AccountType.SAVINGS,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    RecurringTemplate onTarget =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "On account"));
    templateRepository.save(
        RecurringTemplate.create(UUID.randomUUID(), categoryId, otherAccountId, "Elsewhere"));

    assertThat(templateRepository.findByAccountId(accountId))
        .extracting(RecurringTemplate::getId)
        .containsExactly(onTarget.getId());
  }

  @Test
  void findAllReturnsEveryTemplate() {
    RecurringTemplate a =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "A"));
    RecurringTemplate b =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "B"));

    assertThat(templateRepository.findAll())
        .extracting(RecurringTemplate::getId)
        .contains(a.getId(), b.getId());
  }

  @Test
  void existsByCategoryIdReflectsWhetherAnyTemplateReferencesIt() {
    // Backs CategoryService's delete guard (post-F007 schema audit): a category with a template
    // but no transactions must still be undeletable.
    assertThat(templateRepository.existsByCategoryId(categoryId)).isFalse();

    templateRepository.save(
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));

    assertThat(templateRepository.existsByCategoryId(categoryId)).isTrue();
  }
}
