package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.recurringtemplate.PendingRecurringOccurrence;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplate;
import com.chm.myfinances.domain.recurringtemplate.RecurringTemplateVersion;
import com.chm.myfinances.testsupport.FakeIdGenerator;
import com.chm.myfinances.testsupport.FakePendingRecurringOccurrenceRepository;
import com.chm.myfinances.testsupport.FakeRecurringTemplateRepository;
import com.chm.myfinances.testsupport.FakeRecurringTemplateVersionRepository;
import java.math.BigDecimal;
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
  private final RecurringOccurrenceCatchUpService service =
      new RecurringOccurrenceCatchUpService(
          templateRepository, versionRepository, pendingRepository, idGenerator);

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
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), template.getId(), BigDecimal.TEN, 10, YearMonth.of(2026, 1)));

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
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), template.getId(), BigDecimal.TEN, 20, YearMonth.of(2026, 1)));

    service.runCatchUp(LocalDate.of(2026, 2, 5));

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void skipsAnInactiveTemplateEntirely() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent");
    template.close();
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), template.getId(), BigDecimal.TEN, 10, YearMonth.of(2026, 1)));

    service.runCatchUp(LocalDate.of(2026, 6, 20));

    assertThat(pendingRepository.findAll()).isEmpty();
  }

  @Test
  void multiMonthCatchUpGeneratesOnePendingOccurrencePerMissedCycle() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), template.getId(), BigDecimal.TEN, 10, YearMonth.of(2026, 1)));

    service.runCatchUp(LocalDate.of(2026, 5, 20));

    assertThat(pendingRepository.findAll()).hasSize(4);
  }

  @Test
  void runningCatchUpTwiceForTheSameCycleDoesNotDuplicateThePendingOccurrence() {
    RecurringTemplate template =
        templateRepository.save(
            RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    templateRepository.save(template);
    versionRepository.save(
        RecurringTemplateVersion.create(
            UUID.randomUUID(), template.getId(), BigDecimal.TEN, 10, YearMonth.of(2026, 1)));

    service.runCatchUp(LocalDate.of(2026, 2, 15));
    service.runCatchUp(LocalDate.of(2026, 2, 20));

    assertThat(pendingRepository.findAll()).hasSize(1);
  }

  @Test
  void aTemplateWithNoVersionsYetIsSkippedWithoutError() {
    templateRepository.save(
        RecurringTemplate.create(UUID.randomUUID(), categoryId, accountId, "Rent"));

    service.runCatchUp(LocalDate.of(2026, 6, 20));

    assertThat(pendingRepository.findAll()).isEmpty();
  }
}
