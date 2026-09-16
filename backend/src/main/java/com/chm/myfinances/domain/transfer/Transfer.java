package com.chm.myfinances.domain.transfer;

import com.chm.myfinances.domain.shared.TextFieldConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Transfer aggregate (PRD S5.5, S6.2, F005 spec): movement of money between two of the user's own
 * accounts - most commonly paying a credit card statement from checking. Unlike {@code
 * Transaction}, a Transfer has no category and no budget impact (PRD S5.5) - it explicitly isn't
 * "categorized".
 *
 * <p>{@code amount} is always stored positive, same convention as {@code Transaction}. Its
 * two-sided balance effect (source decreases; destination's effect depends on the destination
 * account's own {@code AccountType}) is computed by F003's {@code AccountBalanceQuery}, not stored
 * here.
 *
 * <p>Invariant: {@code fromAccountId != toAccountId} - a transfer to the same account is
 * meaningless. Enforced here at the domain level (not just the API layer), same spirit as {@code
 * Account.requireOpen()}. Both accounts being open at creation time is an application-layer concern
 * (F005 spec) - checked by {@code TransferService} against F003's {@code AccountRepository}, since
 * the domain layer never depends on another aggregate's repository.
 *
 * <p>{@code description} (mandatory) and {@code additionalNotes} (optional) are bounded free-text
 * fields, sharing the {@link TextFieldConstraints} convention used by {@code Transaction} (F004).
 *
 * <p>No versioning, no editing beyond a plain field update - deleting is a hard delete (F005 spec),
 * same as {@code Transaction}.
 */
public final class Transfer {

  private final UUID id;
  private LocalDate date;
  private UUID fromAccountId;
  private UUID toAccountId;
  private BigDecimal amount;
  private String description;
  private String additionalNotes;

  private Transfer(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    this.amount = requireValidAmount(amount);
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
  }

  /** Creates a brand-new Transfer. {@code id} must come from the {@code IdGenerator} port. */
  public static Transfer create(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    return new Transfer(id, date, fromAccountId, toAccountId, amount, description, additionalNotes);
  }

  /** Rebuilds a Transfer from already-validated persisted state. */
  public static Transfer reconstitute(
      UUID id,
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    return new Transfer(id, date, fromAccountId, toAccountId, amount, description, additionalNotes);
  }

  /**
   * Plain in-place edit of every field except {@code id} (F005 spec, same as {@code Transaction}).
   */
  public void edit(
      LocalDate date,
      UUID fromAccountId,
      UUID toAccountId,
      BigDecimal amount,
      String description,
      String additionalNotes) {
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
    this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId must not be null");
    requireDifferentAccounts(fromAccountId, toAccountId);
    this.amount = requireValidAmount(amount);
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
  }

  private static void requireDifferentAccounts(UUID fromAccountId, UUID toAccountId) {
    if (fromAccountId.equals(toAccountId)) {
      throw new IllegalArgumentException("fromAccountId and toAccountId must not be the same");
    }
  }

  private static BigDecimal requireValidAmount(BigDecimal value) {
    Objects.requireNonNull(value, "amount must not be null");
    if (value.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
    return value;
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

  private static String requireValidAdditionalNotes(String value) {
    if (value != null && value.length() > TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH) {
      throw new IllegalArgumentException(
          "additionalNotes must not exceed "
              + TextFieldConstraints.MAX_ADDITIONAL_NOTES_LENGTH
              + " characters");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public LocalDate getDate() {
    return date;
  }

  public UUID getFromAccountId() {
    return fromAccountId;
  }

  public UUID getToAccountId() {
    return toAccountId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getDescription() {
    return description;
  }

  public String getAdditionalNotes() {
    return additionalNotes;
  }
}
