package com.chm.myfinances.domain.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link PendingRecurringOccurrence} (F007 spec): a lightweight,
 * immutable record of one not-yet-confirmed cycle, created once per cycle by the catch-up algorithm
 * and deleted once confirmed or dismissed (application-layer concerns - this class only holds the
 * data).
 */
class PendingRecurringOccurrenceTest {

  private static final UUID TEMPLATE_ID = UUID.randomUUID();
  private static final UUID TEMPLATE_VERSION_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    LocalDate dueDate = LocalDate.of(2026, 3, 15);

    PendingRecurringOccurrence occurrence =
        PendingRecurringOccurrence.create(id, TEMPLATE_ID, TEMPLATE_VERSION_ID, dueDate);

    assertThat(occurrence.getId()).isEqualTo(id);
    assertThat(occurrence.getTemplateId()).isEqualTo(TEMPLATE_ID);
    assertThat(occurrence.getTemplateVersionId()).isEqualTo(TEMPLATE_VERSION_ID);
    assertThat(occurrence.getDueDate()).isEqualTo(dueDate);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(
            () ->
                PendingRecurringOccurrence.create(
                    null, TEMPLATE_ID, TEMPLATE_VERSION_ID, LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullTemplateId() {
    assertThatThrownBy(
            () ->
                PendingRecurringOccurrence.create(
                    UUID.randomUUID(), null, TEMPLATE_VERSION_ID, LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullTemplateVersionId() {
    assertThatThrownBy(
            () ->
                PendingRecurringOccurrence.create(
                    UUID.randomUUID(), TEMPLATE_ID, null, LocalDate.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullDueDate() {
    assertThatThrownBy(
            () ->
                PendingRecurringOccurrence.create(
                    UUID.randomUUID(), TEMPLATE_ID, TEMPLATE_VERSION_ID, null))
        .isInstanceOf(NullPointerException.class);
  }
}
