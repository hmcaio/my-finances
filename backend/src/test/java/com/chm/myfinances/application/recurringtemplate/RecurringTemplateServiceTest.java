package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.account.AccountType;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.testsupport.FakeAccountRepository;
import com.chm.myfinances.testsupport.FakeCategoryRepository;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.FakePendingRecurringOccurrenceRepository;
import com.chm.myfinances.testsupport.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.FakeRecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.FakeTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link RecurringTemplateService}, written first (ADR 0004) against
 * hand-written fakes for every repository port it depends on - plain JUnit, no Spring context.
 * Covers F007 spec's create/setCap (new-version-vs-same-month-replace, same as F006's {@code
 * BudgetService.setCap}), stop/reactivate, the account-closed auto-deactivation path, and -
 * plan.md's explicit test-first item - the confirm flow: an override at confirmation time never
 * creates a new {@code RecurringTemplateVersion}, only a one-off variance on the resulting {@code
 * Transaction}.
 */
class RecurringTemplateServiceTest {

  private final FakeRecurringTemplateRepository templateRepository =
      new FakeRecurringTemplateRepository();
  private final FakeRecurringTemplateVersionRepository versionRepository =
      new FakeRecurringTemplateVersionRepository();
  private final FakePendingRecurringOccurrenceRepository pendingRepository =
      new FakePendingRecurringOccurrenceRepository();
  private final FakeCategoryRepository categoryRepository = new FakeCategoryRepository();
  private final FakeAccountRepository accountRepository = new FakeAccountRepository();
  private final FakePaymentMethodRepository paymentMethodRepository =
      new FakePaymentMethodRepository();
  private final FakeTransactionRepository transactionRepository = new FakeTransactionRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final TransactionService transactionService =
      new TransactionService(
          transactionRepository,
          categoryRepository,
          accountRepository,
          paymentMethodRepository,
          idGenerator);
  private final RecurringOccurrenceCatchUpService catchUpService =
      new RecurringOccurrenceCatchUpService(
          templateRepository, versionRepository, pendingRepository, idGenerator);
  private final RecurringTemplateService service =
      new RecurringTemplateService(
          templateRepository,
          versionRepository,
          pendingRepository,
          categoryRepository,
          accountRepository,
          transactionService,
          catchUpService,
          idGenerator);

  private UUID categoryId;
  private UUID accountId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent", CategoryType.EXPENSE))
            .getId();
    accountId =
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
    paymentMethodId =
        paymentMethodRepository.save(PaymentMethod.create(UUID.randomUUID(), "Debit")).getId();
  }

  @Test
  void createAssignsIdAndSavesAFirstVersion() {
    UUID nextTemplateId = UUID.randomUUID();
    RecurringTemplateService service =
        new RecurringTemplateService(
            templateRepository,
            versionRepository,
            pendingRepository,
            categoryRepository,
            accountRepository,
            transactionService,
            catchUpService,
            new FakeIdGenerator(nextTemplateId));

    RecurringTemplate created =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

    assertThat(created.getId()).isEqualTo(nextTemplateId);
    assertThat(created.isActive()).isTrue();
    List<RecurringTemplateVersion> versions = versionRepository.findByTemplateId(created.getId());
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).getAmount()).isEqualByComparingTo("1500.00");
    assertThat(versions.get(0).getDayOfMonth()).isEqualTo(5);
  }

  @Test
  void createRejectsUnknownCategory() {
    assertThatThrownBy(
            () ->
                service.create(
                    UUID.randomUUID(), accountId, "Rent", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(CategoryNotFoundException.class);
  }

  @Test
  void createRejectsUnknownAccount() {
    assertThatThrownBy(
            () ->
                service.create(
                    categoryId, UUID.randomUUID(), "Rent", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(AccountNotFoundException.class);
  }

  @Test
  void createRejectsAClosedAccount() {
    Account closed = accountRepository.findById(accountId).orElseThrow();
    closed.close();
    accountRepository.save(closed);

    assertThatThrownBy(
            () -> service.create(categoryId, accountId, "Rent", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void findByIdOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.findById(UUID.randomUUID()))
        .isInstanceOf(RecurringTemplateNotFoundException.class);
  }

  @Test
  void setCapForANewMonthCreatesAnAdditionalVersion() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

    service.setCap(template.getId(), new BigDecimal("1600.00"), 10, YearMonth.of(2026, 3));

    assertThat(versionRepository.findByTemplateId(template.getId())).hasSize(2);
  }

  @Test
  void setCapForAnAlreadyVersionedMonthReplacesRatherThanDuplicates() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

    service.setCap(template.getId(), new BigDecimal("1750.00"), 12, YearMonth.of(2026, 1));

    List<RecurringTemplateVersion> versions = versionRepository.findByTemplateId(template.getId());
    assertThat(versions).hasSize(1);
    assertThat(versions.get(0).getAmount()).isEqualByComparingTo("1750.00");
    assertThat(versions.get(0).getDayOfMonth()).isEqualTo(12);
  }

  @Test
  void setCapOfUnknownTemplateThrowsNotFound() {
    assertThatThrownBy(
            () -> service.setCap(UUID.randomUUID(), BigDecimal.TEN, 5, YearMonth.of(2026, 2)))
        .isInstanceOf(RecurringTemplateNotFoundException.class);
  }

  @Test
  void stopDeactivatesTheTemplateAndDeletesItsPendingOccurrences() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));

    RecurringTemplate stopped = service.stop(template.getId());

    assertThat(stopped.isActive()).isFalse();
    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void stopOfUnknownTemplateThrowsNotFound() {
    assertThatThrownBy(() -> service.stop(UUID.randomUUID()))
        .isInstanceOf(RecurringTemplateNotFoundException.class);
  }

  @Test
  void reactivateSetsActiveTrueAndSkipsTheStoppedPeriod() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    service.stop(template.getId());

    RecurringTemplate reactivated = service.reactivate(template.getId());

    assertThat(reactivated.isActive()).isTrue();
    assertThat(reactivated.getLastGeneratedFor()).isEqualTo(YearMonth.now().minusMonths(1));
  }

  @Test
  void deactivateForAccountStopsEveryActiveTemplateOnThatAccountAndLeavesOthersAlone() {
    RecurringTemplate onAccount =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    UUID otherAccountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Savings",
                    null,
                    AccountType.SAVINGS,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();
    RecurringTemplate onOtherAccount =
        service.create(
            categoryId, otherAccountId, "Other", new BigDecimal("10.00"), 5, YearMonth.of(2026, 1));

    service.deactivateForAccount(accountId);

    assertThat(templateRepository.findById(onAccount.getId()).orElseThrow().isActive()).isFalse();
    assertThat(templateRepository.findById(onOtherAccount.getId()).orElseThrow().isActive())
        .isTrue();
  }

  @Test
  void findAllPendingTriggersCatchUpAndReturnsGeneratedOccurrences() {
    // effectiveFrom two months ago with a day-of-month that's certainly already passed this
    // month, so this is deterministic regardless of when the test suite runs.
    service.create(
        categoryId,
        accountId,
        "Rent",
        new BigDecimal("1500.00"),
        1,
        YearMonth.now().minusMonths(2));

    List<PendingRecurringOccurrence> pending = service.findAllPending();

    assertThat(pending).isNotEmpty();
  }

  @Test
  void confirmPendingWithNoOverridesUsesTheTemplatesDefaults() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));

    Transaction confirmed =
        service.confirmPending(
            pending.getId(),
            new ConfirmOccurrenceOverrides(null, null, null, paymentMethodId, null, null));

    assertThat(confirmed.getAmount()).isEqualByComparingTo("1500.00");
    assertThat(confirmed.getDate()).isEqualTo(LocalDate.of(2026, 2, 5));
    assertThat(confirmed.getAccountId()).isEqualTo(accountId);
    assertThat(confirmed.getCategoryId()).isEqualTo(categoryId);
    assertThat(confirmed.getRecurringTemplateVersionId()).isEqualTo(version.getId());
    assertThat(confirmed.getDescription()).isEqualTo("Rent");
    assertThat(pendingRepository.findById(pending.getId())).isEmpty();
    // Confirming with no overrides never creates a new version.
    assertThat(versionRepository.findByTemplateId(template.getId())).hasSize(1);
  }

  @Test
  void confirmPendingWithOverridesUsesThemInsteadAndStillCreatesNoNewVersion() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));
    UUID otherAccountId =
        accountRepository
            .save(
                Account.create(
                    UUID.randomUUID(),
                    "Savings",
                    null,
                    AccountType.SAVINGS,
                    BigDecimal.ZERO,
                    LocalDate.now()))
            .getId();

    Transaction confirmed =
        service.confirmPending(
            pending.getId(),
            new ConfirmOccurrenceOverrides(
                new BigDecimal("1650.00"),
                LocalDate.of(2026, 2, 7),
                otherAccountId,
                paymentMethodId,
                "Rent (adjusted)",
                "Landlord raised rent this month"));

    assertThat(confirmed.getAmount()).isEqualByComparingTo("1650.00");
    assertThat(confirmed.getDate()).isEqualTo(LocalDate.of(2026, 2, 7));
    assertThat(confirmed.getAccountId()).isEqualTo(otherAccountId);
    assertThat(confirmed.getDescription()).isEqualTo("Rent (adjusted)");
    assertThat(confirmed.getAdditionalNotes()).isEqualTo("Landlord raised rent this month");
    // The template's own version history is untouched by a confirmation-time override.
    assertThat(versionRepository.findByTemplateId(template.getId())).hasSize(1);
    assertThat(versionRepository.findByTemplateId(template.getId()).get(0).getAmount())
        .isEqualByComparingTo("1500.00");
  }

  @Test
  void confirmPendingOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(
            () ->
                service.confirmPending(
                    UUID.randomUUID(),
                    new ConfirmOccurrenceOverrides(null, null, null, paymentMethodId, null, null)))
        .isInstanceOf(PendingRecurringOccurrenceNotFoundException.class);
  }

  @Test
  void dismissPendingDeletesTheRowWithoutCreatingATransaction() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));

    service.dismissPending(pending.getId());

    assertThat(pendingRepository.findById(pending.getId())).isEmpty();
    assertThat(
            transactionRepository
                .findAll(
                    new com.chm.myfinances.domain.transaction.TransactionFilter(
                        null, null, null, null, null),
                    org.springframework.data.domain.Pageable.unpaged())
                .getContent())
        .isEmpty();
  }

  @Test
  void dismissPendingOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.dismissPending(UUID.randomUUID()))
        .isInstanceOf(PendingRecurringOccurrenceNotFoundException.class);
  }
}
