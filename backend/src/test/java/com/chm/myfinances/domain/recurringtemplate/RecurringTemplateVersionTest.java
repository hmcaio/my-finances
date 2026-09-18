package com.chm.myfinances.domain.recurringtemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Domain-level unit tests for {@link RecurringTemplateVersion} (PRD S5.7, F007 spec), written
 * before {@link RecurringTemplateVersion} itself (ADR 0004). Covers field invariants, day-of-month
 * bounds (1-31), version resolution (same {@code resolveEffective} rule as F006's {@code
 * BudgetVersion}), and the day-of-month-to-short-month clamping rule this feature documents: day 31
 * on a 30-day month generates on day 30; day 29/30/31 in February generates on its last day.
 */
class RecurringTemplateVersionTest {

  private static final UUID TEMPLATE_ID = UUID.randomUUID();

  @Test
  void createsWithGivenFields() {
    UUID id = UUID.randomUUID();
    YearMonth effectiveFrom = YearMonth.of(2026, 3);

    RecurringTemplateVersion version =
        RecurringTemplateVersion.create(
            id, TEMPLATE_ID, new BigDecimal("1500.00"), 5, effectiveFrom);

    assertThat(version.getId()).isEqualTo(id);
    assertThat(version.getTemplateId()).isEqualTo(TEMPLATE_ID);
    assertThat(version.getAmount()).isEqualByComparingTo("1500.00");
    assertThat(version.getDayOfMonth()).isEqualTo(5);
    assertThat(version.getEffectiveFrom()).isEqualTo(effectiveFrom);
  }

  @Test
  void createRejectsNullId() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    null, TEMPLATE_ID, BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullTemplateId() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), null, BigDecimal.TEN, 5, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullEffectiveFrom() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, 5, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsNullAmount() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), TEMPLATE_ID, null, 5, YearMonth.now()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void createRejectsZeroOrNegativeAmount() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), TEMPLATE_ID, BigDecimal.ZERO, 5, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsDayOfMonthBelowOne() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, 0, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createRejectsDayOfMonthAbove31() {
    assertThatThrownBy(
            () ->
                RecurringTemplateVersion.create(
                    UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, 32, YearMonth.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createAcceptsDayOfMonthBoundaries() {
    RecurringTemplateVersion first =
        RecurringTemplateVersion.create(
            UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, 1, YearMonth.now());
    RecurringTemplateVersion last =
        RecurringTemplateVersion.create(
            UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, 31, YearMonth.now());

    assertThat(first.getDayOfMonth()).isEqualTo(1);
    assertThat(last.getDayOfMonth()).isEqualTo(31);
  }

  @Test
  void updateReplacesAmountAndDayOfMonthInPlace() {
    RecurringTemplateVersion version = version(5, YearMonth.of(2026, 3));

    version.update(new BigDecimal("999.00"), 10);

    assertThat(version.getAmount()).isEqualByComparingTo("999.00");
    assertThat(version.getDayOfMonth()).isEqualTo(10);
    // effectiveFrom/templateId/id are untouched - only amount/day are corrected in place.
    assertThat(version.getEffectiveFrom()).isEqualTo(YearMonth.of(2026, 3));
  }

  @Test
  void updateRejectsAnInvalidDayOfMonth() {
    RecurringTemplateVersion version = version(5, YearMonth.of(2026, 3));

    assertThatThrownBy(() -> version.update(BigDecimal.TEN, 32))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dueDateForUsesTheDayOfMonthDirectlyOnALongEnoughMonth() {
    RecurringTemplateVersion version = version(15, YearMonth.of(2026, 1));

    assertThat(version.dueDateFor(YearMonth.of(2026, 3))).isEqualTo(LocalDate.of(2026, 3, 15));
  }

  @Test
  void dueDateForClamps31ToTheLastDayOfA30DayMonth() {
    RecurringTemplateVersion version = version(31, YearMonth.of(2026, 1));

    // April has 30 days - day 31 clamps to day 30 (documented clamping rule, F007 spec).
    assertThat(version.dueDateFor(YearMonth.of(2026, 4))).isEqualTo(LocalDate.of(2026, 4, 30));
  }

  @Test
  void dueDateForClamps29Through31ToFebruarysLastDay() {
    RecurringTemplateVersion version = version(31, YearMonth.of(2026, 1));

    // 2026 is not a leap year - February has 28 days.
    assertThat(version.dueDateFor(YearMonth.of(2026, 2))).isEqualTo(LocalDate.of(2026, 2, 28));
  }

  @Test
  void dueDateForClamps29ToFebruary29OnALeapYear() {
    RecurringTemplateVersion version = version(29, YearMonth.of(2026, 1));

    assertThat(version.dueDateFor(YearMonth.of(2028, 2))).isEqualTo(LocalDate.of(2028, 2, 29));
  }

  @Test
  void resolveEffectivePicksTheLatestVersionAtOrBeforeTheTargetMonth() {
    RecurringTemplateVersion january = version(1, YearMonth.of(2026, 1));
    RecurringTemplateVersion march = version(5, YearMonth.of(2026, 3));
    RecurringTemplateVersion june = version(10, YearMonth.of(2026, 6));

    Optional<RecurringTemplateVersion> resolved =
        RecurringTemplateVersion.resolveEffective(
            List.of(january, march, june), YearMonth.of(2026, 5));

    assertThat(resolved).contains(march);
  }

  @Test
  void resolveEffectiveIgnoresVersionsAfterTheTargetMonth() {
    RecurringTemplateVersion future = version(1, YearMonth.of(2026, 12));

    Optional<RecurringTemplateVersion> resolved =
        RecurringTemplateVersion.resolveEffective(List.of(future), YearMonth.of(2026, 1));

    assertThat(resolved).isEmpty();
  }

  @Test
  void resolveEffectiveReturnsEmptyWhenNoVersionsGiven() {
    assertThat(RecurringTemplateVersion.resolveEffective(List.of(), YearMonth.of(2026, 1)))
        .isEmpty();
  }

  private static RecurringTemplateVersion version(int dayOfMonth, YearMonth effectiveFrom) {
    return RecurringTemplateVersion.create(
        UUID.randomUUID(), TEMPLATE_ID, BigDecimal.TEN, dayOfMonth, effectiveFrom);
  }
}
