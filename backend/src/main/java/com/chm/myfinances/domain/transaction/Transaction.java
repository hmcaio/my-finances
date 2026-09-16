package com.chm.myfinances.domain.transaction;

import com.chm.myfinances.domain.category.CategoryType;
import com.chm.myfinances.domain.shared.DescriptionConstraints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Transaction aggregate (PRD S5.3, F004 spec). The core ledger entry: every account's running
 * balance (F003's {@code AccountBalanceQuery}) is computed by summing these.
 *
 * <p>{@code amount} is always stored positive - whether it increases or decreases the account's
 * balance is derived from {@code type} and the account's {@code AccountType} at read time (F003
 * spec), not duplicated here.
 *
 * <p>{@code type} is captured at creation (denormalized from the category's own, already-immutable
 * type - F002 spec) rather than always re-read from the category, so a stable, redundant
 * data-integrity check is available even though {@code Category.type} can never itself change.
 *
 * <p>{@code recurringTemplateVersionId} is set once, at creation, and never touched by {@link
 * #edit}: PRD S5.7 - "a single past transaction can still be hand-edited directly (amount/date/
 * account) without touching the version chain". F007 doesn't exist yet, so this is always {@code
 * null} for now, but the field/column already exists ahead of it.
 *
 * <p>Editing (amount/date/category/account/payment method/description/additional notes) is a plain
 * in-place update - no versioning, unlike Budget/RecurringTemplate (F004 spec). Deleting is a hard
 * delete - a transaction has no downstream history that would be orphaned by removing it.
 *
 * <p>{@code description} (mandatory) and {@code additionalNotes} (optional) are bounded free-text
 * fields, following the convention established for taxonomy "name" fields (F002/F003's {@code
 * NameConstraints}) but for narrative fields ({@code DescriptionConstraints}): a length check here,
 * a matching {@code @Size} on the request DTOs, and a matching {@code varchar(n)} column.
 */
public final class Transaction {

  private final UUID id;
  private LocalDate date;
  private BigDecimal amount;
  private UUID categoryId;
  private CategoryType type;
  private UUID accountId;
  private UUID paymentMethodId;
  private final UUID recurringTemplateVersionId;
  private String description;
  private String additionalNotes;

  private Transaction(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes) {
    this.id = Objects.requireNonNull(id, "id must not be null");
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.amount = requireValidAmount(amount);
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.paymentMethodId =
        Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
    this.recurringTemplateVersionId = recurringTemplateVersionId;
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
  }

  /** Creates a brand-new Transaction. {@code id} must come from the {@code IdGenerator} port. */
  public static Transaction create(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes) {
    return new Transaction(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes);
  }

  /** Rebuilds a Transaction from already-validated persisted state. */
  public static Transaction reconstitute(
      UUID id,
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      UUID recurringTemplateVersionId,
      String description,
      String additionalNotes) {
    return new Transaction(
        id,
        date,
        amount,
        categoryId,
        type,
        accountId,
        paymentMethodId,
        recurringTemplateVersionId,
        description,
        additionalNotes);
  }

  /**
   * Plain in-place edit of every field except {@code id} and {@code recurringTemplateVersionId}
   * (F004 spec). {@code categoryId}/{@code type} are updated together - the caller (application
   * layer) re-derives {@code type} from whatever category is being assigned, same as at creation.
   */
  public void edit(
      LocalDate date,
      BigDecimal amount,
      UUID categoryId,
      CategoryType type,
      UUID accountId,
      UUID paymentMethodId,
      String description,
      String additionalNotes) {
    this.date = Objects.requireNonNull(date, "date must not be null");
    this.amount = requireValidAmount(amount);
    this.categoryId = Objects.requireNonNull(categoryId, "categoryId must not be null");
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
    this.paymentMethodId =
        Objects.requireNonNull(paymentMethodId, "paymentMethodId must not be null");
    this.description = requireValidDescription(description);
    this.additionalNotes = requireValidAdditionalNotes(additionalNotes);
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
    if (value.length() > DescriptionConstraints.MAX_DESCRIPTION_LENGTH) {
      throw new IllegalArgumentException(
          "description must not exceed "
              + DescriptionConstraints.MAX_DESCRIPTION_LENGTH
              + " characters");
    }
    return value;
  }

  private static String requireValidAdditionalNotes(String value) {
    if (value != null && value.length() > DescriptionConstraints.MAX_ADDITIONAL_NOTES_LENGTH) {
      throw new IllegalArgumentException(
          "additionalNotes must not exceed "
              + DescriptionConstraints.MAX_ADDITIONAL_NOTES_LENGTH
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

  public BigDecimal getAmount() {
    return amount;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public CategoryType getType() {
    return type;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public UUID getPaymentMethodId() {
    return paymentMethodId;
  }

  public UUID getRecurringTemplateVersionId() {
    return recurringTemplateVersionId;
  }

  public String getDescription() {
    return description;
  }

  public String getAdditionalNotes() {
    return additionalNotes;
  }
}
