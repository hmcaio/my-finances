package com.chm.myfinances.infrastructure.persistence.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.TestcontainersConfiguration;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountRepository;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryRepository;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrenceRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateRepository;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration test for {@link PendingRecurringOccurrenceRepositoryAdapter},
 * against a real Testcontainers Postgres (ADR 0010).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PendingRecurringOccurrenceRepositoryAdapterTest {

  @Autowired private PendingRecurringOccurrenceRepository pendingRepository;
  @Autowired private RecurringTemplateRepository templateRepository;
  @Autowired private RecurringTemplateVersionRepository versionRepository;
  @Autowired private CategoryRepository categoryRepository;
  @Autowired private AccountRepository accountRepository;

  private UUID templateId;
  private UUID versionId;

  @BeforeEach
  void setUp() {
    UUID categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent Test", CategoryType.EXPENSE))
            .getId();
    UUID accountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Checking",
                    null,
                    AccountType.CHECKING,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    templateId =
        templateRepository
            .save(RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"))
            .getId();
    versionId =
        versionRepository
            .save(
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), templateId, BigDecimal.TEN, 5, YearMonth.of(2026, 1)))
            .getId();
  }

  @Test
  void savesAndReloadsAPendingOccurrence() {
    PendingRecurringOccurrence occurrence =
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 3, 5));

    pendingRepository.save(occurrence);

    assertThat(pendingRepository.findById(occurrence.getId())).isPresent();
  }

  @Test
  void findAllReturnsEveryPendingOccurrence() {
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 3, 5)));
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 4, 5)));

    assertThat(pendingRepository.findAll()).hasSize(2);
  }

  @Test
  void deleteByIdRemovesTheRow() {
    PendingRecurringOccurrence occurrence =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 3, 5)));

    pendingRepository.deleteById(occurrence.getId());

    assertThat(pendingRepository.findById(occurrence.getId())).isEmpty();
  }

  @Test
  void deleteByTemplateIdRemovesEveryOccurrenceForThatTemplate() {
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 3, 5)));
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 4, 5)));

    pendingRepository.deleteByTemplateId(templateId);

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void existsByTemplateIdAndDueDateDetectsAnAlreadyGeneratedCycle() {
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), templateId, versionId, LocalDate.of(2026, 3, 5)));

    assertThat(pendingRepository.existsByTemplateIdAndDueDate(templateId, LocalDate.of(2026, 3, 5)))
        .isTrue();
    assertThat(pendingRepository.existsByTemplateIdAndDueDate(templateId, LocalDate.of(2026, 4, 5)))
        .isFalse();
  }
}
