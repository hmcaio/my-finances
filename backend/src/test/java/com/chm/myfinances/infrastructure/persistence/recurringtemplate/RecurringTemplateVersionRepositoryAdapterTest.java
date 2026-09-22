package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.institution.InstitutionRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.DatabaseIntegrationTest;
import com.chm.myfinances.testsupport.mothers.TestFixtures;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Persistence-layer integration test for {@link RecurringTemplateVersionRepositoryAdapter}, against
 * a real Testcontainers Postgres (ADR 0010) - including the {@code YearMonth}<->first-of-
 * month-{@code date} round trip and the {@code (template_id, effective_from)} unique constraint
 * from {@code V9__recurring_templates.sql}.
 */
@DatabaseIntegrationTest
class RecurringTemplateVersionRepositoryAdapterTest {

  @Autowired private InstitutionRepository institutionRepository;
  @Autowired private RecurringTemplateVersionRepository versionRepository;
  @Autowired private RecurringTemplateRepository templateRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;

  private UUID templateId;

  @BeforeEach
  void setUp() {
    UUID categoryId =
        TestFixtures.category(categoryRepository, "Rent Test", CategoryType.EXPENSE).getId();
    UUID accountId =
        TestFixtures.checkingAccount(accountRepository, institutionRepository, "Checking").getId();
    templateId =
        templateRepository
            .save(RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"))
            .getId();
  }

  @Test
  void savesAndReloadsAVersionRoundTrippingTheYearMonth() {
    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, new BigDecimal("1500.00"), 5, YearMonth.of(2026, 3));

    versionRepository.save(version);

    Optional<RecurringTemplateVersion> reloaded =
        versionRepository.findByTemplateIdAndEffectiveFrom(templateId, YearMonth.of(2026, 3));
    assertThat(reloaded).isPresent();
    assertThat(reloaded.get().getAmount()).isEqualByComparingTo("1500.00");
    assertThat(reloaded.get().getDayOfMonth()).isEqualTo(5);
    assertThat(reloaded.get().getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void findByTemplateIdReturnsEveryVersionForThatTemplate() {
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.of(2026, 1)));
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, new BigDecimal("20.00"), 10, YearMonth.of(2026, 4)));

    assertThat(versionRepository.findByTemplateId(templateId)).hasSize(2);
  }

  @Test
  void findByIdReturnsTheVersion() {
    RecurringTemplateVersion version =
        versionRepository.save(
            RecurringTemplateVersion.create(
                UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.of(2026, 1)));

    assertThat(versionRepository.findById(version.getId())).isPresent();
  }

  @Test
  void saveOfAnExistingVersionUpdatesRatherThanDuplicating() {
    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.of(2026, 3));
    versionRepository.save(version);

    version.update(new BigDecimal("99.00"), 12);
    versionRepository.save(version);

    assertThat(versionRepository.findByTemplateId(templateId)).hasSize(1);
    assertThat(
            versionRepository
                .findByTemplateIdAndEffectiveFrom(templateId, YearMonth.of(2026, 3))
                .orElseThrow()
                .getAmount())
        .isEqualByComparingTo("99.00");
  }
}
