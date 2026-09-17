package com.chm.myfinances.domain.recurringtemplate;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * RecurringTemplate aggregate (PRD S5.7, F007 spec): a versioned recurring bill/income template
 * whose {@link RecurringTemplateVersion} history tracks amount/day-of-month changes over time, same
 * versioned-history shape as F006's {@code Budget}/{@code BudgetVersion}.
 *
 * <p>{@code lastGeneratedFor} tracks the last cycle a {@link PendingRecurringOccurrence} was
 * generated for (F007 spec) - the simplest way to know how many cycles have elapsed without a
 * separate table. It is {@code null} until the first occurrence is generated, and is only advanced
 * by {@link #advanceLastGeneratedFor} (the catch-up algorithm, {@link
 * RecurringOccurrenceGenerator}) - never by {@link #close()}/{@link #reactivate}, except that
 * {@link #reactivate} deliberately resets it forward past the stopped period (see that method's
 * javadoc).
 *
 * <p>{@code description} is a bounded mandatory free-text field, same {@link TextFieldConstraints}
 * convention as F004's {@code Transaction.description}. Unlike {@code categoryId}/{@code
 * accountId}, it has no separate mutator - the F007 spec's API surface (create/list/edit-cap/stop/
 * reactivate) never edits it after creation, same "fixed at creation" treatment as {@code
 * Budget.categoryId}.
 */
public final class RecurringTemplate {

  private final UUID id;
  private final UUID categoryId;
  private final UUID accountId;
  private final String description;
  private boolean active;
  private YearMonth lastGeneratedFor;

  private RecurringTemplate(
      UUID id,
      UUID categoryId,
      UUID accountId,
      String description,
      boolean active,
      YearMonth lastGeneratedFor) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.description = requireValidDescription(description);
    this.active = active;
    this.lastGeneratedFor = lastGeneratedFor;
  }

  /**
   * Creates a brand-new, active RecurringTemplate with no generation history yet. {@code id} must
   * come from the {@code IdGenerator} port.
   */
  public static RecurringTemplate create(
      UUID id, UUID categoryId, UUID accountId, String description) {
    return new RecurringTemplate(id, categoryId, accountId, description, true, null);
  }

  /** Rebuilds a RecurringTemplate from already-validated persisted state. */
  public static RecurringTemplate reconstitute(
      UUID id,
      UUID categoryId,
      UUID accountId,
      String description,
      boolean active,
      YearMonth lastGeneratedFor) {
    return new RecurringTemplate(id, categoryId, accountId, description, active, lastGeneratedFor);
  }

  /**
   * Deactivates this template - the same operation whether triggered by the user manually stopping
   * it (F007 spec's {@code POST .../stop}) or by F003's {@link
   * com.chm.myfinances.domain.account.AccountClosedNotifier} port auto-deactivating every template
   * pointed at a closed account (PRD S5.4). Deliberately idempotent (unlike {@code
   * Account.close()}) since those two triggers can both reach an already-inactive template without
   * either being an error.
   */
  public void close() {
    this.active = false;
  }

  /**
   * Reactivates this template, resuming generation from {@code asOf} onward rather than catching up
   * on the entire stopped period (PRD S5.7: "Reactivating ... resumes generation from the current
   * version"). Achieved by fast-forwarding {@code lastGeneratedFor} to the month before {@code
   * asOf} - so the next catch-up run only generates {@code asOf} onward, exactly as if the template
   * had been active all along up to that point.
   */
  public void reactivate(YearMonth asOf) {
    Objects.requireNonNull(asOf, "asOf must not be null");
    this.active = true;
    this.lastGeneratedFor = asOf.minusMonths(1);
  }

  /**
   * Records that a {@link PendingRecurringOccurrence} has now been generated for every cycle up to
   * and including {@code cycle} - called by the catch-up algorithm ({@link
   * RecurringOccurrenceGenerator}), never directly by a user-facing use case.
   */
  public void advanceLastGeneratedFor(YearMonth cycle) {
    this.lastGeneratedFor = Objects.requireNonNull(cycle, "cycle must not be null");
  }

  private static String requireValidDescription(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("description must not be blank");
    }
    if (value.length() > TextFieldConstraints.MAX_DESCRIPTION_LENGTH) {
      throw new IllegalArgumentException(
          "description must not exceed "
              + TextFieldConstraints.MAX_DESCRIPTION_LENGTH
              + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }

  public YearMonth getLastGeneratedFor() {
    return lastGeneratedFor;
  }
}
