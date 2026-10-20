package com.chm.myfinances.application.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Direct test for {@link ConfirmOccurrenceOverrides}: a plain record with no invariants of its own
 * - the null-falls-back-to-the-occurrence's-defaults behavior it documents is implemented by {@code
 * RecurringTemplateService.confirmPending}, not here - so this only pins the field round-trip,
 * including that every field accepts {@code null} (all six are optional overrides).
 */
class ConfirmOccurrenceOverridesTest {

  @Test
  void accessorsReturnTheConstructedValues() {
    BigDecimal amount = new BigDecimal("42.00");
    LocalDate date = LocalDate.of(2026, 3, 5);
    UUID accountId = UUID.randomUUID();
    UUID paymentMethodId = UUID.randomUUID();

    ConfirmOccurrenceOverrides overrides =
        new ConfirmOccurrenceOverrides(amount, date, accountId, paymentMethodId, "Rent", "note");

    assertThat(overrides.amount()).isEqualTo(amount);
    assertThat(overrides.date()).isEqualTo(date);
    assertThat(overrides.accountId()).isEqualTo(accountId);
    assertThat(overrides.paymentMethodId()).isEqualTo(paymentMethodId);
    assertThat(overrides.description()).isEqualTo("Rent");
    assertThat(overrides.additionalNotes()).isEqualTo("note");
  }

  @Test
  void everyFieldAcceptsNull() {
    ConfirmOccurrenceOverrides overrides =
        new ConfirmOccurrenceOverrides(null, null, null, null, null, null);

    assertThat(overrides.amount()).isNull();
    assertThat(overrides.date()).isNull();
    assertThat(overrides.accountId()).isNull();
    assertThat(overrides.paymentMethodId()).isNull();
    assertThat(overrides.description()).isNull();
    assertThat(overrides.additionalNotes()).isNull();
  }
}
