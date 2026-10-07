package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import com.chm.myfinances.application.account.AccountNotFoundException;
import com.chm.myfinances.application.category.CategoryNotFoundException;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.account.Account;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.paymentmethod.PaymentMethod;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.domain.transaction.Transaction;
import com.chm.myfinances.domain.transaction.TransactionFilter;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeCategoryRepository;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakeInvestmentHoldingRepository;
import com.chm.myfinances.testsupport.fakes.FakePaymentMethodRepository;
import com.chm.myfinances.testsupport.fakes.FakePendingRecurringOccurrenceRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.fakes.FakeTransactionRepository;
import com.chm.myfinances.testsupport.fakes.FakeVehicleRepository;
import com.chm.myfinances.testsupport.mothers.AccountMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
  private final FakeVehicleRepository vehicleRepository = new FakeVehicleRepository();
  private final FakeInvestmentHoldingRepository investmentHoldingRepository =
      new FakeInvestmentHoldingRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final TransactionService transactionService =
      new TransactionService(
          transactionRepository,
          categoryRepository,
          accountRepository,
          paymentMethodRepository,
          vehicleRepository,
          investmentHoldingRepository,
          idGenerator);
  private final RecurringOccurrenceCatchUpService catchUpService =
      new RecurringOccurrenceCatchUpService(
          templateRepository,
          versionRepository,
          pendingRepository,
          idGenerator,
          Clock.systemDefaultZone());
  private final RecurringTemplateService service =
      new RecurringTemplateService(
          templateRepository,
          versionRepository,
          pendingRepository,
          categoryRepository,
          accountRepository,
          transactionService,
          catchUpService,
          idGenerator,
          Clock.systemDefaultZone());

  private UUID categoryId;
  private UUID accountId;
  private UUID paymentMethodId;

  @BeforeEach
  void setUp() {
    categoryId =
        categoryRepository
            .save(Category.create(UUID.randomUUID(), "Rent", CategoryType.EXPENSE))
            .getId();
    accountId = accountRepository.save(AccountMother.checking().build()).getId();
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
            new FakeIdGenerator(nextTemplateId),
            Clock.systemDefaultZone());

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
    closed.close(LocalDate.now());
    accountRepository.save(closed);

    assertThatThrownBy(
            () -> service.create(categoryId, accountId, "Rent", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(AccountClosedException.class);
  }

  @Test
  void createRejectsAnInvestmentAccount() {
    UUID investmentId = accountRepository.save(AccountMother.investment().build()).getId();

    assertThatThrownBy(
            () ->
                service.create(
                    categoryId, investmentId, "Rent", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(AccountTypeNotAllowedException.class);
    assertThat(service.findAll()).isEmpty();
  }

  @Test
  void createRejectsTheFuelCategory() {
    UUID fuelCategoryId =
        categoryRepository
            .save(
                Category.reconstitute(UUID.randomUUID(), "Fuel", CategoryType.EXPENSE, false, true))
            .getId();

    assertThatThrownBy(
            () ->
                service.create(
                    fuelCategoryId, accountId, "Fill-up", BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(FuelCategoryNotAllowedException.class);
    assertThat(service.findAll()).isEmpty();
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
  void setCapForANewMonthRepointsAffectedPendingOccurrencesToTheNewVersion() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion januaryVersion =
        versionRepository.findByTemplateId(template.getId()).get(0);
    // Generated before the edit, for a March cycle that (at generation time) still resolved to
    // the January version - nothing existed yet for March specifically.
    PendingRecurringOccurrence marchOccurrence =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(),
                template.getId(),
                januaryVersion.getId(),
                LocalDate.of(2026, 3, 5)));

    RecurringTemplateVersion marchVersion =
        service.setCap(template.getId(), new BigDecimal("1600.00"), 10, YearMonth.of(2026, 3));

    PendingRecurringOccurrence reloaded =
        pendingRepository.findById(marchOccurrence.getId()).orElseThrow();
    assertThat(reloaded.getTemplateVersionId()).isEqualTo(marchVersion.getId());
  }

  @Test
  void setCapForANewMonthLeavesEarlierCyclesPendingOccurrencesUntouched() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion januaryVersion =
        versionRepository.findByTemplateId(template.getId()).get(0);
    // A still-pending January occurrence - correctly resolves to the January version both before
    // and after a later, forward-only March version is added; must not be rewritten (F007 spec:
    // a cap change never rewrites what a past month showed).
    PendingRecurringOccurrence januaryOccurrence =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(),
                template.getId(),
                januaryVersion.getId(),
                LocalDate.of(2026, 1, 5)));

    service.setCap(template.getId(), new BigDecimal("1600.00"), 10, YearMonth.of(2026, 3));

    PendingRecurringOccurrence reloaded =
        pendingRepository.findById(januaryOccurrence.getId()).orElseThrow();
    assertThat(reloaded.getTemplateVersionId()).isEqualTo(januaryVersion.getId());
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
    UUID otherAccountId = accountRepository.save(AccountMother.savings().build()).getId();
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
    UUID otherAccountId = accountRepository.save(AccountMother.savings().build()).getId();

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
                .findAll(new TransactionFilter(null, null, null, null, null), Pageable.unpaged())
                .getContent())
        .isEmpty();
  }

  @Test
  void dismissPendingOfUnknownIdThrowsNotFound() {
    assertThatThrownBy(() -> service.dismissPending(UUID.randomUUID()))
        .isInstanceOf(PendingRecurringOccurrenceNotFoundException.class);
  }

  @Test
  void setCapForANewMonthLogsANewVersionLineWithoutTheAmount() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      service.setCap(template.getId(), new BigDecimal("1612.34"), 10, YearMonth.of(2026, 3));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly(
              "Recurring template " + template.getId() + ": new version effective 2026-03");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void setCapForAnAlreadyVersionedMonthLogsAReplacedLineWithoutTheAmount() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      service.setCap(template.getId(), new BigDecimal("1612.34"), 12, YearMonth.of(2026, 1));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly(
              "Recurring template " + template.getId() + ": version effective 2026-01 replaced");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void deactivateForAccountLogsHowManyTemplatesItDeactivated() {
    service.create(
        categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    service.create(
        categoryId, accountId, "Internet", new BigDecimal("80.00"), 7, YearMonth.of(2026, 1));
    RecurringTemplate alreadyStopped =
        service.create(
            categoryId, accountId, "Gym", new BigDecimal("50.00"), 9, YearMonth.of(2026, 1));
    service.stop(alreadyStopped.getId());

    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      service.deactivateForAccount(accountId);

      // Only the two still-active templates count; the already-stopped one is not re-deactivated.
      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Deactivated 2 template(s) for closed account " + accountId);
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void deactivateForAccountWithNothingToDeactivateLogsNoInfoLine() {
    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      service.deactivateForAccount(accountId);

      assertThat(logs.eventsAt(Level.INFO)).isEmpty();
    }
  }

  @Test
  void confirmPendingLogsThePendingIdAndTheCreatedTransactionId() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));

    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      Transaction confirmed =
          service.confirmPending(
              pending.getId(),
              new ConfirmOccurrenceOverrides(null, null, null, paymentMethodId, null, null));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly(
              "Pending occurrence "
                  + pending.getId()
                  + " confirmed as transaction "
                  + confirmed.getId());
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void dismissPendingLogsThePendingId() {
    RecurringTemplate template =
        service.create(
            categoryId, accountId, "Rent", new BigDecimal("1500.00"), 5, YearMonth.of(2026, 1));
    RecurringTemplateVersion version = versionRepository.findByTemplateId(template.getId()).get(0);
    PendingRecurringOccurrence pending =
        pendingRepository.save(
            PendingRecurringOccurrence.create(
                UUID.randomUUID(), template.getId(), version.getId(), LocalDate.of(2026, 2, 5)));

    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      service.dismissPending(pending.getId());

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly("Pending occurrence " + pending.getId() + " dismissed");
      assertThat(logs.events()).hasSize(1);
    }
  }

  @Test
  void aRejectedDismissOfAnUnknownPendingIdLogsNothing() {
    try (LogCapture logs = LogCapture.of(RecurringTemplateService.class)) {
      assertThatThrownBy(() -> service.dismissPending(UUID.randomUUID()))
          .isInstanceOf(PendingRecurringOccurrenceNotFoundException.class);

      assertThat(logs.events()).isEmpty();
    }
  }
}
