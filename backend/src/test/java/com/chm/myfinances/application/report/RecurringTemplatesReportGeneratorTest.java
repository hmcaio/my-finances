package com.chm.myfinances.application.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.application.auditlog.AuditReferenceLabels;
import com.chm.myfinances.application.recurringtemplate.RecurringOccurrenceCatchUpService;
import com.chm.myfinances.application.recurringtemplate.RecurringTemplateService;
import com.chm.myfinances.application.transaction.TransactionService;
import com.chm.myfinances.domain.category.Category;
import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.testsupport.CsvTestSupport;
import com.chm.myfinances.testsupport.TestClocks;
import com.chm.myfinances.testsupport.fakes.FakeAccountRepository;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
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
import com.chm.myfinances.testsupport.mothers.RecurringTemplateMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateVersionMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RecurringTemplatesReportGenerator} (F030 spec), written first (ADR 0004): only
 * active templates appear, the current cycle's version is used while its due date hasn't passed
 * yet, and the following cycle's otherwise.
 */
class RecurringTemplatesReportGeneratorTest {

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
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final TransactionService transactionService =
      new TransactionService(
          transactionRepository,
          categoryRepository,
          accountRepository,
          paymentMethodRepository,
          vehicleRepository,
          investmentHoldingRepository,
          idGenerator,
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));
  private final RecurringOccurrenceCatchUpService catchUpService =
      new RecurringOccurrenceCatchUpService(
          templateRepository,
          versionRepository,
          pendingRepository,
          idGenerator,
          Clock.systemDefaultZone(),
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));
  private final RecurringTemplateService templateService =
      new RecurringTemplateService(
          templateRepository,
          versionRepository,
          pendingRepository,
          categoryRepository,
          accountRepository,
          transactionService,
          catchUpService,
          idGenerator,
          Clock.systemDefaultZone(),
          new AuditRecorder(auditLog, AuditReferenceLabels.none()));

  private RecurringTemplatesReportGenerator generatorAsOf(LocalDate asOf) {
    return new RecurringTemplatesReportGenerator(
        templateService,
        versionRepository,
        categoryRepository,
        accountRepository,
        TestClocks.fixedAtStartOf(asOf));
  }

  private UUID category() {
    return categoryRepository
        .save(Category.create(UUID.randomUUID(), "Rent Category R Test", CategoryType.EXPENSE))
        .getId();
  }

  private UUID account() {
    return accountRepository
        .save(AccountMother.checking().withName("Checking R Test").build())
        .getId();
  }

  private UUID template(
      UUID categoryId, UUID accountId, String description, String amount, int dayOfMonth) {
    RecurringTemplate t =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .withDescription(description)
                .build());
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(t.getId())
            .withAmount(new BigDecimal(amount))
            .withDayOfMonth(dayOfMonth)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());
    return t.getId();
  }

  @Test
  void showsTheCurrentCyclesDueDateWhenItHasNotPassedYet() {
    UUID categoryId = category();
    UUID accountId = account();
    template(categoryId, accountId, "Rent R Test", "1500.00", 20);

    List<GeneratedFile> files =
        generatorAsOf(LocalDate.of(2026, 6, 10)).generate(ReportFilter.none());

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("description")).isEqualTo("Rent R Test");
    assertThat(rows.get(0).get("amount")).isEqualTo("1500.00");
    assertThat(rows.get(0).get("day_of_month")).isEqualTo("20");
    assertThat(rows.get(0).get("next_occurrence")).isEqualTo("2026-06-20");
  }

  @Test
  void movesToTheFollowingCycleWhenTheCurrentOnesDueDateHasAlreadyPassed() {
    UUID categoryId = category();
    UUID accountId = account();
    template(categoryId, accountId, "Rent R Test 2", "1500.00", 5);

    List<GeneratedFile> files =
        generatorAsOf(LocalDate.of(2026, 6, 10)).generate(ReportFilter.none());

    List<Map<String, String>> rows = CsvTestSupport.parseSingleSection(files.get(0).content());
    assertThat(rows.get(0).get("next_occurrence")).isEqualTo("2026-07-05");
  }

  @Test
  void inactiveTemplatesAreExcluded() {
    UUID categoryId = category();
    UUID accountId = account();
    UUID templateId = template(categoryId, accountId, "Stopped R Test", "100.00", 1);
    templateService.stop(templateId);

    List<GeneratedFile> files =
        generatorAsOf(LocalDate.of(2026, 6, 10)).generate(ReportFilter.none());

    assertThat(CsvTestSupport.parseSingleSection(files.get(0).content())).isEmpty();
  }
}
