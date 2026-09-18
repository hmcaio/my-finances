package com.chm.myfinances.domain.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import com.chm.myfinances.domain.recurringtemplate.RecurringOccurrenceGenerator.CatchUpResult;
import com.chm.myfinances.domain.recurringtemplate.RecurringOccurrenceGenerator.PendingCycle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RecurringOccurrenceGenerator}, the pure, unit-testable core of F007's
 * lazy/catch-up pending-occurrence generation (PRD S5.7, F007 spec, plan.md's highest-priority
 * test-first item). Written before {@link RecurringOccurrenceGenerator} itself (ADR 0004).
 *
 * <p>Deliberately plain-JUnit and persistence-free: every scenario is expressed as (active flag,
 * versions, {@code lastGeneratedFor}, "today") in, ({@code List<PendingCycle>}, advanced {@code
 * lastGeneratedFor}) out - so the rules-heavy part of this feature is fully covered without a
 * database, per PRD S7.2's TDD emphasis.
 */
class RecurringOccurrenceGeneratorTest {

  private static final UUID TEMPLATE_ID = UUID.randomUUID();

  @Test
  void inactiveTemplateGeneratesNothing() {
    RecurringTemplateVersion version = version(10, YearMonth.of(2026, 1));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            false, List.of(version), YearMonth.of(2026, 1), LocalDate.of(2026, 6, 20));

    assertThat(result.cyclesToGenerate()).isEmpty();
    // lastGeneratedFor is untouched while stopped - nothing was actually processed.
    assertThat(result.advancedLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 1));
  }

  @Test
  void noVersionsGeneratesNothing() {
    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(true, List.of(), null, LocalDate.of(2026, 6, 20));

    assertThat(result.cyclesToGenerate()).isEmpty();
    assertThat(result.advancedLastGeneratedFor()).isNull();
  }

  @Test
  void normalMonthlyTickGeneratesTheOneNewCycleWhenItsDayHasPassed() {
    RecurringTemplateVersion version = version(10, YearMonth.of(2026, 1));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 2), LocalDate.of(2026, 3, 15));

    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::cycle)
        .containsExactly(YearMonth.of(2026, 3));
    assertThat(result.cyclesToGenerate().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 3, 10));
    assertThat(result.advancedLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void normalMonthlyTickGeneratesNothingWhenThisMonthsDayHasNotPassedYet() {
    RecurringTemplateVersion version = version(10, YearMonth.of(2026, 1));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 2), LocalDate.of(2026, 3, 5));

    assertThat(result.cyclesToGenerate()).isEmpty();
    // Not advanced past February - March is still undetermined until day 10 passes, so a later
    // call this same month must reconsider it rather than silently skipping it forever.
    assertThat(result.advancedLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 2));
  }

  @Test
  void generatesExactlyOnTheDueDateItself() {
    RecurringTemplateVersion version = version(10, YearMonth.of(2026, 1));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 2), LocalDate.of(2026, 3, 10));

    assertThat(result.cyclesToGenerate()).hasSize(1);
  }

  @Test
  void multiMonthCatchUpAfterDowntimeGeneratesEveryMissedCycleDatedForItsOwnMonth() {
    RecurringTemplateVersion version = version(10, YearMonth.of(2026, 1));

    // The app was off from February through May; last_generated_for is still January.
    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 1), LocalDate.of(2026, 6, 20));

    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::cycle)
        .containsExactly(
            YearMonth.of(2026, 2),
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6));
    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::dueDate)
        .containsExactly(
            LocalDate.of(2026, 2, 10),
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 4, 10),
            LocalDate.of(2026, 5, 10),
            LocalDate.of(2026, 6, 10));
    assertThat(result.advancedLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 6));
  }

  @Test
  void firstEverGenerationWithNoLastGeneratedForStartsFromTheEarliestVersionsMonth() {
    RecurringTemplateVersion version = version(5, YearMonth.of(2026, 3));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), null, LocalDate.of(2026, 6, 10));

    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::cycle)
        .containsExactly(
            YearMonth.of(2026, 3),
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6));
    assertThat(result.advancedLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 6));
  }

  @Test
  void dayOfMonthClampsToTheLastDayOfAShortMonth() {
    RecurringTemplateVersion version = version(31, YearMonth.of(2026, 1));

    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 3), LocalDate.of(2026, 4, 30));

    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::dueDate)
        .containsExactly(LocalDate.of(2026, 4, 30));
  }

  @Test
  void versionChangeMidCatchUpUsesWhicheverVersionWasEffectiveForEachPastMonth() {
    RecurringTemplateVersion original = version(5, YearMonth.of(2026, 1));
    RecurringTemplateVersion updated = version(20, YearMonth.of(2026, 4));

    // Downtime spanned the version change: Feb/Mar should use the original version (day 5), Apr
    // onward should use the updated one (day 20) - F007 spec: "using whichever version was
    // effective for that specific past month, not necessarily the current version".
    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(original, updated), YearMonth.of(2026, 1), LocalDate.of(2026, 6, 25));

    assertThat(result.cyclesToGenerate())
        .extracting(c -> c.version().getDayOfMonth())
        .containsExactly(5, 5, 20, 20, 20);
    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::dueDate)
        .containsExactly(
            LocalDate.of(2026, 2, 5),
            LocalDate.of(2026, 3, 5),
            LocalDate.of(2026, 4, 20),
            LocalDate.of(2026, 5, 20),
            LocalDate.of(2026, 6, 20));
  }

  @Test
  void reactivationsFastForwardedLastGeneratedForSkipsTheStoppedPeriodEntirely() {
    RecurringTemplateVersion version = version(5, YearMonth.of(2026, 1));

    // Simulates RecurringTemplate.reactivate(YearMonth.of(2026, 6)): lastGeneratedFor was fast-
    // forwarded to May, so catch-up resumes cleanly from June rather than backfilling the months
    // the template spent stopped.
    CatchUpResult result =
        RecurringOccurrenceGenerator.catchUp(
            true, List.of(version), YearMonth.of(2026, 5), LocalDate.of(2026, 6, 10));

    assertThat(result.cyclesToGenerate())
        .extracting(PendingCycle::cycle)
        .containsExactly(YearMonth.of(2026, 6));
  }

  private static RecurringTemplateVersion version(int dayOfMonth, YearMonth effectiveFrom) {
    return RecurringTemplateVersion.create(
        UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, dayOfMonth, effectiveFrom);
  }
}
