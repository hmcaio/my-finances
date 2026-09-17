package com.chm.myfinances.domain.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link RecurringTemplate} (PRD S5.7, F007 spec), written before
 * {@link RecurringTemplate} itself (ADR 0004). Covers plan.md's "close()/active toggling" checklist
 * item: {@code active} defaults true, {@code close()} deactivates (same operation whether triggered
 * by the user manually stopping a template or F003's account-closed port), and {@code reactivate()}
 * resumes generation from "now" rather than catching up on the entire stopped period.
 */
class RecurringTemplateTest {

  private static final UUID CATEGORY_ID = UUID.randomUUID();
  private static final UUID ACCOUNT_ID = UUID.randomUUID();

  @Test
  void createsActiveByDefaultWithNoGenerationHistory() {
    UUID id = UUID.randomUUID();

    RecurringTemplate template = RecurringTemplate.create(id, CATEGORY_ID, ACCOUNT_ID, "Rent");

    assertThat(template.getId()).isEqualTo(id);
    assertThat(template.getCategoryId()).isEqualTo(CATEGORY_ID);
    assertThat(template.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(template.getDescription()).isEqualTo("Rent");
    assertThat(template.isActive()).isTrue();
    assertThat(template.getLastGeneratedFor()).isNull();
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(() -> RecurringTemplate.create(null, CATEGORY_ID, ACCOUNT_ID, "Rent"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullCategoryId() {
    assertThatThrownBy(() -> RecurringTemplate.create(UUID.randomUUID(), null, ACCOUNT_ID, "Rent"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullAccountId() {
    assertThatThrownBy(() -> RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, null, "Rent"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsBlankDescription() {
    assertThatThrownBy(
            () -> RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsDescriptionOverTheMaxLength() {
    String tooLong = "x".repeat(151);
    assertThatThrownBy(
            () -> RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsDescriptionAtExactlyTheMaxLength() {
    String maxLength = "x".repeat(150);

    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, maxLength);

    assertThat(template.getDescription()).isEqualTo(maxLength);
  }

  @Test
  void closeDeactivatesAnActiveTemplate() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "Rent");

    template.close();

    assertThat(template.isActive()).isFalse();
  }

  @Test
  void closeIsIdempotentOnAnAlreadyInactiveTemplate() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "Rent");
    template.close();

    // Closing again (e.g. the user stops it, then its account is separately closed too) must not
    // throw - unlike Account.close(), this is deliberately a no-op rather than a guarded operation,
    // since two independent triggers (manual stop, F003's account-closed port) can both call it.
    template.close();

    assertThat(template.isActive()).isFalse();
  }

  @Test
  void reactivateSetsActiveTrue() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "Rent");
    template.close();

    template.reactivate(YearMonth.of(2026, 6));

    assertThat(template.isActive()).isTrue();
  }

  @Test
  void reactivateResumesFromTheGivenMonthRatherThanCatchingUpTheStoppedPeriod() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "Rent");
    template.advanceLastGeneratedFor(YearMonth.of(2026, 1));
    template.close();

    // Reactivated in June, long after it was stopped in January - PRD S5.7's "resumes generation
    // from the current version" means no backlog for the stopped Feb-May period, not a flood of
    // four months' worth of pending occurrences.
    template.reactivate(YearMonth.of(2026, 6));

    assertThat(template.getLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 5));
  }

  @Test
  void advanceLastGeneratedForUpdatesTheTrackedCycle() {
    RecurringTemplate template =
        RecurringTemplate.create(UUID.randomUUID(), CATEGORY_ID, ACCOUNT_ID, "Rent");

    template.advanceLastGeneratedFor(YearMonth.of(2026, 3));

    assertThat(template.getLastGeneratedFor()).isEqualTo(YearMonth.of(2026, 3));
  }
}
