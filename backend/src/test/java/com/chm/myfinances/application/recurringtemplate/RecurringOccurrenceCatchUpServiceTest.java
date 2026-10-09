package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.chm.myfinances.application.auditlog.AuditAction;
import com.chm.myfinances.application.auditlog.AuditEntityType;
import com.chm.myfinances.application.auditlog.AuditOrigin;
import com.chm.myfinances.application.auditlog.AuditRecorder;
import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.testsupport.LogCapture;
import com.chm.myfinances.testsupport.fakes.FakeAuditLog;
import com.chm.myfinances.testsupport.fakes.FakeIdGenerator;
import com.chm.myfinances.testsupport.fakes.FakePendingRecurringOccurrenceRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.fakes.FakeRecurringTemplateVersionRepository;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateMother;
import com.chm.myfinances.testsupport.mothers.RecurringTemplateVersionMother;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Application-layer tests for {@link RecurringOccurrenceCatchUpService}, written first (ADR 0004)
 * against hand-written fakes - plain JUnit, no Spring context. The generation rules themselves are
 * already fully covered by {@code RecurringOccurrenceGeneratorTest}; this class only verifies the
 * wiring: pending rows actually get persisted, {@code lastGeneratedFor} actually advances, and an
 * already-generated cycle is never duplicated.
 */
class RecurringOccurrenceCatchUpServiceTest {

  private final FakeRecurringTemplateRepository templateRepository =
      new FakeRecurringTemplateRepository();
  private final FakeRecurringTemplateVersionRepository versionRepository =
      new FakeRecurringTemplateVersionRepository();
  private final FakePendingRecurringOccurrenceRepository pendingRepository =
      new FakePendingRecurringOccurrenceRepository();
  private final FakeIdGenerator idGenerator = new FakeIdGenerator();
  private final FakeAuditLog auditLog = new FakeAuditLog();
  private final RecurringOccurrenceCatchUpService service =
      new RecurringOccurrenceCatchUpService(
          templateRepository,
          versionRepository,
          pendingRepository,
          idGenerator,
          Clock.systemDefaultZone(),
          new AuditRecorder(auditLog));

  private UUID categoryId;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    categoryId = UUID.randomUUID();
    accountId = UUID.randomUUID();
  }

  @Test
  void generatesAPendingOccurrenceAndAdvancesLastGeneratedFor() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .build());
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(10)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());

    service.runCatchUp(LocalDate.of(2026, 2, 15));

    assertThat(pendingRepository.findAll()).hasSize(1);
    PendingRecurringOccurrence occurrence = pendingRepository.findAll().get(0);
    assertThat(occurrence.getTemplateId()).isEqualTo(template.getId());
    assertThat(occurrence.getDueDate()).isEqualTo(LocalDate.of(2026, 2, 10));
    assertThat(templateRepository.findById(template.getId()).orElseThrow().getLastGeneratedFor())
        .isEqualTo(YearMonth.of(2026, 2));
  }

  @Test
  void doesNotGenerateWhenThisMonthsDayHasNotPassedYet() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .build());
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(20)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());

    service.runCatchUp(LocalDate.of(2026, 2, 5));

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void skipsAnInactiveTemplateEntirely() {
    RecurringTemplate template =
        RecurringTemplateMother.template()
            .withCategoryId(categoryId)
            .withAccountId(accountId)
            .build();
    template.close();
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(10)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());

    service.runCatchUp(LocalDate.of(2026, 6, 20));

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void multiMonthCatchUpGeneratesOnePendingOccurrencePerMissedCycle() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .build());
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(10)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());

    service.runCatchUp(LocalDate.of(2026, 5, 20));

    assertThat(pendingRepository.findAll()).hasSize(4);
  }

  @Test
  void runningCatchUpTwiceForTheSameCycleDoesNotDuplicateThePendingOccurrence() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .build());
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(10)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());

    service.runCatchUp(LocalDate.of(2026, 2, 15));
    service.runCatchUp(LocalDate.of(2026, 2, 20));

    assertThat(pendingRepository.findAll()).hasSize(1);
  }

  @Test
  void aTemplateWithNoVersionsYetIsSkippedWithoutError() {
    templateRepository.save(
        RecurringTemplateMother.template()
            .withCategoryId(categoryId)
            .withAccountId(accountId)
            .build());

    service.runCatchUp(LocalDate.of(2026, 6, 20));

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void logsAnInfoSummaryOfWhatItGeneratedWhenSomethingWasGenerated() {
    seedTemplate("Rent", YearMonth.of(2026, 2));
    seedTemplate("Internet", YearMonth.of(2026, 2));
    seedTemplate("Insurance", YearMonth.of(2026, 1));

    try (LogCapture logs = LogCapture.of(RecurringOccurrenceCatchUpService.class)) {
      service.runCatchUp(LocalDate.of(2026, 3, 15));

      assertThat(logs.messagesAt(Level.INFO))
          .containsExactly(
              "Recurring catch-up generated 4 pending occurrence(s) across 3 template(s)");
      assertThat(logs.eventsAt(Level.ERROR)).isEmpty();
    }
    assertThat(pendingRepository.findAll()).hasSize(4);
  }

  @Test
  void aCycleAnotherRunAlreadyInsertedIsNeitherDuplicatedNorCounted() {
    seedTemplate("Rent", YearMonth.of(2026, 2));
    RecurringTemplate template = templateRepository.findAllActive().get(0);
    UUID versionId = versionRepository.findByTemplateId(template.getId()).get(0).getId();
    // A concurrent run got there first, after this one had already decided the cycle was due.
    pendingRepository.save(
        PendingRecurringOccurrence.create(
            UUID.randomUUID(), template.getId(), versionId, LocalDate.of(2026, 3, 10)));

    try (LogCapture logs = LogCapture.of(RecurringOccurrenceCatchUpService.class)) {
      service.runCatchUp(LocalDate.of(2026, 3, 15));

      assertThat(pendingRepository.findAll()).hasSize(1);
      assertThat(logs.eventsAt(Level.INFO)).isEmpty();
    }
    assertThat(templateRepository.findById(template.getId()).orElseThrow().getLastGeneratedFor())
        .isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void logsNoInfoLineWhenNothingWasGenerated() {
    try (LogCapture logs = LogCapture.of(RecurringOccurrenceCatchUpService.class)) {
      service.runCatchUp(LocalDate.of(2026, 3, 15));

      assertThat(logs.eventsAt(Level.INFO)).isEmpty();
      assertThat(logs.messagesAt(Level.DEBUG))
          .containsExactly("Recurring catch-up generated 0 pending occurrence(s)");
    }
  }

  @Test
  void logsNoInfoLineOnASecondRunThatFindsNothingNew() {
    seedTemplate("Rent", YearMonth.of(2026, 2));
    service.runCatchUp(LocalDate.of(2026, 3, 15));

    try (LogCapture logs = LogCapture.of(RecurringOccurrenceCatchUpService.class)) {
      service.runCatchUp(LocalDate.of(2026, 3, 16));

      assertThat(logs.eventsAt(Level.INFO)).isEmpty();
    }
  }

  @Test
  void neverLogsTheTemplateDescription() {
    seedTemplate("Distinctive Landlord Name", YearMonth.of(2026, 2));

    try (LogCapture logs = LogCapture.of(RecurringOccurrenceCatchUpService.class)) {
      service.runCatchUp(LocalDate.of(2026, 3, 15));

      assertThat(logs.events())
          .isNotEmpty()
          .allSatisfy(
              event ->
                  assertThat(event.getFormattedMessage()).doesNotContain("Distinctive Landlord"));
    }
  }

  @Test
  void recordsOneGeneratedSummaryEntryPerTemplatePerRunNotOnePerOccurrence() {
    seedTemplate("Rent", YearMonth.of(2025, 11));

    service.runCatchUp(LocalDate.of(2026, 3, 15));

    assertThat(auditLog.entries()).hasSize(1);
    var entry = auditLog.onlyEntry();
    assertThat(entry.entityType()).isEqualTo(AuditEntityType.RECURRING_TEMPLATE);
    assertThat(entry.action()).isEqualTo(AuditAction.GENERATED);
    assertThat(entry.origin()).isEqualTo(AuditOrigin.SYSTEM);
    assertThat(entry.changes())
        .containsKey("count")
        .containsKey("firstDate")
        .containsKey("lastDate");
  }

  @Test
  void aRunThatGeneratesNothingRecordsNoAuditEntry() {
    seedTemplate("Rent", YearMonth.of(2026, 3));

    service.runCatchUp(LocalDate.of(2026, 3, 15));

    assertThat(auditLog.entries()).isEmpty();
  }

  /**
   * A template (day 10, effective from January) already generated up to {@code lastGeneratedFor}.
   */
  private void seedTemplate(String description, YearMonth lastGeneratedFor) {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplateMother.template()
                .withCategoryId(categoryId)
                .withAccountId(accountId)
                .withDescription(description)
                .build());
    template.advanceLastGeneratedFor(lastGeneratedFor);
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersionMother.version()
            .withTemplateId(template.getId())
            .withAmount(BigDecimal.TEN)
            .withDayOfMonth(10)
            .withEffectiveFrom(YearMonth.of(2026, 1))
            .build());
  }
}
