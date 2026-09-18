package com.chm.myfinances.domain.recurringtemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * RecurringTemplateVersion aggregate (PRD S5.7, F007 spec): a single amount/day-of-month pair,
 * effective from a given month onward, for one {@link RecurringTemplate}. Editing amount/day
 * creates a new version rather than mutating an existing one - same forward-only versioning shape
 * as F006's {@code BudgetVersion} (which this feature was explicitly designed to preview, per
 * F006's own spec.md), including its same-month "replace, don't duplicate" rule, applied at the
 * application layer ({@code RecurringTemplateService}) exactly like {@code BudgetService.setCap}.
 *
 * <p>{@code dayOfMonth} is 1-31, but not every month has 31 days: {@link #dueDateFor} clamps to the
 * last day of a shorter month (F007 spec's documented rule) - e.g. day 31 on a 30-day month
 * generates on day 30, and days 29-31 in a non-leap February generate on February 28.
 */
public final class RecurringTemplateVersion {

  private static final int MIN_DAY_OF_MONTH = 1;
  private static final int MAX_DAY_OF_MONTH = 31;

  private final UUID id;
  private final UUID templateId;
  private BigDecimal amount;
  private int dayOfMonth;
  private final YearMonth effectiveFrom;

  private RecurringTemplateVersion(
      UUID id, UUID templateId, BigDecimal amount, int dayOfMonth, YearMonth effectiveFrom) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.templateId = Objects.requireNonNull(templateId, "templateId must not be null");
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    this.amount = requireValidAmount(amount);
    this.dayOfMonth = requireValidDayOfMonth(dayOfMonth);
  }

  /**
   * Creates a brand-new RecurringTemplateVersion. {@code id} must come from the {@code IdGenerator}
   * port.
   */
  public static RecurringTemplateVersion create(
      UUID id, UUID templateId, BigDecimal amount, int dayOfMonth, YearMonth effectiveFrom) {
    return new RecurringTemplateVersion(id, templateId, amount, dayOfMonth, effectiveFrom);
  }

  /** Rebuilds a RecurringTemplateVersion from already-validated persisted state. */
  public static RecurringTemplateVersion reconstitute(
      UUID id, UUID templateId, BigDecimal amount, int dayOfMonth, YearMonth effectiveFrom) {
    return new RecurringTemplateVersion(id, templateId, amount, dayOfMonth, effectiveFrom);
  }

  /**
   * Replaces this version's amount/day-of-month in place - the one mutation this otherwise-
   * immutable-history aggregate allows, mirroring F006 {@code BudgetVersion.updateCap}'s same-month
   * correction: {@code RecurringTemplateService.setCap} calls this instead of creating a new
   * version when an edit targets a month that already has one. {@code templateId}/{@code
   * effectiveFrom} never change.
   */
  public void update(BigDecimal amount, int dayOfMonth) {
    this.amount = requireValidAmount(amount);
    this.dayOfMonth = requireValidDayOfMonth(dayOfMonth);
  }

  /**
   * The actual date a pending occurrence is due for {@code cycle}, clamping {@code dayOfMonth} to
   * {@code cycle}'s own length when it's shorter (F007 spec's documented clamping rule).
   */
  public LocalDate dueDateFor(YearMonth cycle) {
    Objects.requireNonNull(cycle, "cycle must not be null");
    int clampedDay = Math.min(dayOfMonth, cycle.lengthOfMonth());
    return cycle.atDay(clampedDay);
  }

  private static BigDecimal requireValidAmount(BigDecimal value) {
    Objects.requireNonNull(value, "amount must not be null");
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    return value;
  }

  private static int requireValidDayOfMonth(int value) {
    if (value < MIN_DAY_OF_MONTH || value > MAX_DAY_OF_MONTH) {
      throw new IllegalArgumentException(
          "dayOfMonth must be between " + MIN_DAY_OF_MONTH + " and " + MAX_DAY_OF_MONTH);
    }
    return value;
  }

  /**
   * Resolves whichever of {@code versions} is effective for {@code targetMonth}: the version with
   * the latest {@code effectiveFrom} that is {@code <=} {@code targetMonth} - identical rule to
   * F006's {@code BudgetVersion#resolveEffective}, reused here for both the current-version display
   * ({@code RecurringTemplateCurrentVersionQuery}) and the catch-up generator ({@link
   * RecurringOccurrenceGenerator}), which resolves a different version per historical cycle (F007
   * spec: "using whichever version was effective for that specific past month, not necessarily the
   * current version").
   */
  public static Optional<RecurringTemplateVersion> resolveEffective(
      List<RecurringTemplateVersion> versions, YearMonth targetMonth) {
    Objects.requireNonNull(targetMonth, "targetMonth must not be null");
    return versions.stream()
        .filter(version -> !version.effectiveFrom.isAfter(targetMonth))
        .max(Comparator.comparing(RecurringTemplateVersion::getEffectiveFrom));
  }

  public UUID getId() {
    return id;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public int getDayOfMonth() {
    return dayOfMonth;
  }

  public YearMonth getEffectiveFrom() {
    return effectiveFrom;
  }
}
