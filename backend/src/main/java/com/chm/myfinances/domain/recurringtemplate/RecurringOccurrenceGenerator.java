package com.chm.myfinances.domain.recurringtemplate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure, persistence-free core of F007's lazy/catch-up pending-occurrence generation (PRD S5.7, F007
 * spec) - the highest-value place in this feature to apply PRD S7.2's test-first emphasis. Given
 * one template's {@code active} flag, its full version history, how far it was last generated, and
 * "today", computes exactly which cycles a {@link PendingRecurringOccurrence} should now exist for
 * and how far {@code lastGeneratedFor} should advance. The caller ({@code
 * RecurringOccurrenceCatchUpService}) is the only place this touches persistence - creating the
 * actual {@link PendingRecurringOccurrence} rows and saving the advanced {@link
 * RecurringTemplate#advanceLastGeneratedFor}.
 *
 * <p><b>Algorithm</b> (F007 spec): starting from the month after {@code lastGeneratedFor}
 * (exclusive) - or the earliest version's {@code effectiveFrom} if generation has never run for
 * this template - walk forward one cycle at a time through the current month (inclusive). Every
 * fully-elapsed past month is unconditionally due (its day-of-month has necessarily already
 * passed); the current month is only due once {@code today} reaches its (possibly clamped)
 * day-of-month, and if it isn't due yet, the walk stops there without advancing {@code
 * lastGeneratedFor} into it - so a later call the same month reconsiders it instead of silently
 * skipping it. Each cycle resolves whichever {@link RecurringTemplateVersion} was effective for
 * that specific past month (F007 spec: "not necessarily the current version"), mirroring F006's
 * budget-version resolution.
 */
public final class RecurringOccurrenceGenerator {

  private RecurringOccurrenceGenerator() {}

  /** One cycle due to become a {@link PendingRecurringOccurrence}. */
  public record PendingCycle(
      YearMonth cycle, RecurringTemplateVersion version, LocalDate dueDate) {}

  /**
   * The cycles to generate plus the {@code lastGeneratedFor} value the template should be advanced
   * to (unchanged from the input when nothing new was processed - e.g. an inactive template, no
   * versions yet, or the current month's day hasn't passed).
   */
  public record CatchUpResult(
      List<PendingCycle> cyclesToGenerate, YearMonth advancedLastGeneratedFor) {}

  public static CatchUpResult catchUp(
      boolean active,
      List<RecurringTemplateVersion> versions,
      YearMonth lastGeneratedFor,
      LocalDate today) {
    if (!active || versions.isEmpty()) {
      return new CatchUpResult(List.of(), lastGeneratedFor);
    }

    YearMonth currentMonth = YearMonth.from(today);
    YearMonth earliestVersionMonth =
        versions.stream()
            .map(RecurringTemplateVersion::getEffectiveFrom)
            .min(Comparator.naturalOrder())
            .orElseThrow();
    YearMonth start =
        lastGeneratedFor == null ? earliestVersionMonth : lastGeneratedFor.plusMonths(1);

    List<PendingCycle> cycles = new ArrayList<>();
    YearMonth advanced = lastGeneratedFor;
    for (YearMonth cursor = start; !cursor.isAfter(currentMonth); cursor = cursor.plusMonths(1)) {
      Optional<RecurringTemplateVersion> effective =
          RecurringTemplateVersion.resolveEffective(versions, cursor);
      if (effective.isEmpty()) {
        // No version was effective yet this early (shouldn't normally happen, since `start` is
        // derived from the earliest version's own month) - nothing to generate, and we can't
        // meaningfully advance past a cycle we couldn't resolve.
        continue;
      }

      LocalDate dueDate = effective.get().dueDateFor(cursor);
      boolean isCurrentMonth = cursor.equals(currentMonth);
      if (isCurrentMonth && dueDate.isAfter(today)) {
        break;
      }

      cycles.add(new PendingCycle(cursor, effective.get(), dueDate));
      advanced = cursor;
    }

    return new CatchUpResult(cycles, advanced);
  }
}
